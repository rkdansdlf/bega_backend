package com.example.auth.config;

import com.example.ai.service.AiProxyService;
import com.example.ai.service.AiProxyService.ProxyByteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.profiles.active=local",
        "spring.jwt.secret=test-jwt-secret-32-characters-long",
        "spring.jwt.refresh-expiration=86400000",
        "spring.datasource.url=jdbc:h2:mem:ai_proxy_security_public;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=false",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=6379",
        "spring.data.redis.repositories.enabled=false",
        "jobrunr.background-job-server.enabled=false",
        "jobrunr.dashboard.enabled=false",
        "storage.type=oci",
        "oci.s3.endpoint=http://localhost:4566",
        "oci.s3.access-key=test-access-key",
        "oci.s3.secret-key=test-secret-key",
        "oci.s3.bucket=test-bucket",
        "oci.s3.region=ap-seoul-1",
        "app.ai.proxy.public-in-dev=true",
        "spring.autoconfigure.exclude=io.awspring.cloud.autoconfigure.s3.S3AutoConfiguration"
})
@DisplayName("AI proxy security integration tests (public dev/local mode)")
class AiProxySecurityPublicModeIntegrationTest {

    private static final String CHAT_ENDPOINT = "/api/ai/chat/completion";
    private static final String PAYLOAD = "{\"question\":\"테스트\"}";
    private static final byte[] RESPONSE_BODY = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiProxyService aiProxyService;

    @BeforeEach
    void setUp() {
        reset(aiProxyService);
        given(aiProxyService.forwardJson(eq("/ai/chat/completion"), eq(PAYLOAD)))
                .willReturn(new ProxyByteResponse(HttpStatus.OK, new HttpHeaders(), RESPONSE_BODY));
    }

    @Test
    @DisplayName("public dev/local mode allows unauthenticated AI proxy requests")
    void chatCompletion_allowsUnauthenticatedRequestWhenExplicitlyEnabled() throws Exception {
        mockMvc.perform(post(CHAT_ENDPOINT)
                        .contentType("application/json")
                        .content(PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(content().bytes(RESPONSE_BODY));

        verify(aiProxyService).forwardJson("/ai/chat/completion", PAYLOAD);
    }

    @Test
    @DisplayName("public dev/local mode still allows authenticated AI proxy requests")
    void chatCompletion_allowsAuthenticatedRequestWhenExplicitlyEnabled() throws Exception {
        mockMvc.perform(post(CHAT_ENDPOINT)
                        .with(user("tester").roles("USER"))
                        .contentType("application/json")
                        .content(PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(content().bytes(RESPONSE_BODY));

        verify(aiProxyService, times(1)).forwardJson("/ai/chat/completion", PAYLOAD);
    }
}
