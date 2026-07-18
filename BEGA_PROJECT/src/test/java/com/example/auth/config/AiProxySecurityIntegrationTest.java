package com.example.auth.config;

import com.example.ai.chat.dto.StoredChatMessage;
import com.example.ai.chat.entity.AiChatMessageRole;
import com.example.ai.chat.entity.AiChatMessageStatus;
import com.example.ai.chat.service.AiChatPersistenceService;
import com.example.ai.service.AiProxyService;
import com.example.ai.service.AiProxyService.ProxyByteResponse;
import com.example.common.ratelimit.RateLimitService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.jwt.secret=test-jwt-secret-64-characters-long-for-hs512-signature-tests-key-1234567890",
        "spring.jwt.refresh-expiration=86400000",
        "spring.datasource.url=jdbc:h2:mem:ai_proxy_security_default;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=false",
        "jobrunr.background-job-server.enabled=false",
        "jobrunr.dashboard.enabled=false",
        "storage.type=oci",
        "oci.s3.endpoint=http://localhost:4566",
        "oci.s3.access-key=test-access-key",
        "oci.s3.secret-key=test-secret-key",
        "oci.s3.bucket=test-bucket",
        "oci.s3.region=ap-seoul-1",
        "app.ai.proxy.max-admin-json-bytes=16",
        "app.ai.proxy.max-voice-request-bytes=16",
        "app.ai.proxy.max-chat-persistence-json-bytes=128"
})
@DisplayName("AI proxy security integration tests (default mode)")
class AiProxySecurityIntegrationTest {

    private static final String CHAT_ENDPOINT = "/api/ai/chat/completion";
    private static final String RELEASE_DECISION_PRESETS_ENDPOINT = "/api/ai/release-decision/presets";
    private static final String PAYLOAD = "{\"question\":\"테스트\"}";
    private static final byte[] RESPONSE_BODY = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiProxyService aiProxyService;

    @MockitoBean
    private RateLimitService rateLimitService;

    @MockitoBean
    private AiChatPersistenceService aiChatPersistenceService;

    @BeforeEach
    void setUp() {
        reset(aiProxyService, rateLimitService, aiChatPersistenceService);
        given(rateLimitService.isAllowed(anyString(), anyInt(), anyInt(), anyBoolean())).willReturn(true);
        given(aiProxyService.forwardJson(eq("/ai/chat/completion"), eq(PAYLOAD)))
                .willReturn(new ProxyByteResponse(HttpStatus.OK, new HttpHeaders(), RESPONSE_BODY));
        given(aiProxyService.forwardGet(eq("/ai/release-decision/presets")))
                .willReturn(new ProxyByteResponse(HttpStatus.OK, new HttpHeaders(), RESPONSE_BODY));
    }

    @Test
    @DisplayName("default mode blocks unauthenticated AI proxy requests")
    void chatCompletion_blocksUnauthenticatedRequestByDefault() throws Exception {
        mockMvc.perform(post(CHAT_ENDPOINT)
                        .contentType("application/json")
                        .content(PAYLOAD))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(aiProxyService);
    }

    @Test
    @DisplayName("request-limit filter rejects oversized unauthenticated AI requests before auth and rate limit")
    void chatCompletion_rejectsOversizedUnauthenticatedRequestBeforeSecurityFlow() throws Exception {
        mockMvc.perform(post(CHAT_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversizedChatPayload()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("AI_PROXY_PAYLOAD_TOO_LARGE"));

        verifyNoInteractions(aiProxyService);
        verify(rateLimitService, never()).isAllowed(anyString(), anyInt(), anyInt(), anyBoolean());
    }

    @Test
    @DisplayName("request-limit filter rejects oversized authenticated AI requests before proxy forwarding")
    void chatCompletion_rejectsOversizedAuthenticatedRequestBeforeProxyForwarding() throws Exception {
        mockMvc.perform(post(CHAT_ENDPOINT)
                        .with(user("tester").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversizedChatPayload()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("AI_PROXY_PAYLOAD_TOO_LARGE"));

        verifyNoInteractions(aiProxyService);
        verify(rateLimitService, never()).isAllowed(anyString(), anyInt(), anyInt(), anyBoolean());
    }

    @Test
    @DisplayName("default mode allows authenticated AI proxy requests")
    void chatCompletion_allowsAuthenticatedRequestByDefault() throws Exception {
        mockMvc.perform(post(CHAT_ENDPOINT)
                        .with(user("tester").roles("USER"))
                        .contentType("application/json")
                        .content(PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(content().bytes(RESPONSE_BODY));

        verify(aiProxyService).forwardJson("/ai/chat/completion", PAYLOAD);
    }

    @Test
    @DisplayName("default mode blocks authenticated non-admin users from release decision routes")
    void releaseDecision_blocksAuthenticatedNonAdminByDefault() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                        RELEASE_DECISION_PRESETS_ENDPOINT)
                        .with(user("tester").roles("USER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(aiProxyService);
    }

    @Test
    @DisplayName("default mode allows admin users to access release decision routes")
    void releaseDecision_allowsAdminByDefault() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                        RELEASE_DECISION_PRESETS_ENDPOINT)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(RESPONSE_BODY));

        verify(aiProxyService).forwardGet("/ai/release-decision/presets");
    }

    @Test
    @DisplayName("request-limit filter rejects oversized admin release decision POST before proxy forwarding")
    void releaseDecisionDraft_rejectsOversizedAdminRequestBeforeProxyForwarding() throws Exception {
        mockMvc.perform(post("/api/ai/release-decision/draft")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"prediction_stage2\"}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("AI_PROXY_PAYLOAD_TOO_LARGE"));

        verifyNoInteractions(aiProxyService);
        verify(rateLimitService, never()).isAllowed(anyString(), anyInt(), anyInt(), anyBoolean());
    }

    @Test
    @DisplayName("request-limit filter rejects oversized voice multipart envelope before proxy forwarding")
    void chatVoice_rejectsOversizedMultipartEnvelopeBeforeProxyForwarding() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "voice.wav",
                "audio/wav",
                new byte[] {1, 2, 3, 4});

        mockMvc.perform(multipart("/api/ai/chat/voice")
                        .file(file)
                        .with(user("tester").roles("USER")))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("AI_PROXY_PAYLOAD_TOO_LARGE"));

        verifyNoInteractions(aiProxyService);
        verify(rateLimitService, never()).isAllowed(anyString(), anyInt(), anyInt(), anyBoolean());
    }

    @Test
    @DisplayName("normal AI chat persistence message request still succeeds")
    void chatPersistence_allowsNormalUserMessageRequest() throws Exception {
        given(aiChatPersistenceService.addUserMessage(eq(1L), eq(7L), org.mockito.ArgumentMatchers.any()))
                .willReturn(new StoredChatMessage(
                        100L,
                        7L,
                        AiChatMessageRole.USER,
                        AiChatMessageStatus.COMPLETED,
                        "hello",
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        Instant.now(),
                        Instant.now()));

        mockMvc.perform(post("/api/ai/chat/sessions/7/messages/user")
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                                1L,
                                "n/a",
                                AuthorityUtils.createAuthorityList("ROLE_USER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    private String oversizedChatPayload() {
        return "{\"question\":\"" + "x".repeat(12_500) + "\"}";
    }
}
