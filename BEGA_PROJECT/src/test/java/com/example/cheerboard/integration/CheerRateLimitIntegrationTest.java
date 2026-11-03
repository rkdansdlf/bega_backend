package com.example.cheerboard.integration;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.service.CheerService;
import com.example.common.ratelimit.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:cheer_rate;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=30000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=false",
        "storage.type=oci",
        "oci.s3.endpoint=http://localhost:4566",
        "oci.s3.access-key=test-access-key",
        "oci.s3.secret-key=test-secret-key",
        "oci.s3.bucket=test-bucket",
        "oci.s3.region=ap-seoul-1"
})
class CheerRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private com.example.kbo.repository.TeamRepository teamRepo;

    @MockitoBean
    private RateLimitService rateLimitService;

    @MockitoSpyBean
    private CheerService cheerService;

    private UserEntity user;

    @BeforeEach
    void setUp() {
        if (!teamRepo.existsById("LG")) {
            teamRepo.save(com.example.kbo.entity.TeamEntity.builder()
                    .teamId("LG")
                    .teamName("LG Twins")
                    .teamShortName("LG")
                    .city("Seoul")
                    .build());
        }

        String setupSuffix = UUID.randomUUID().toString().substring(0, 8);
        user = UserEntity.builder()
                .email("rateuser+" + setupSuffix + "@test.com")
                .name("Rate User")
                .handle("rt" + setupSuffix)
                .uniqueId(UUID.randomUUID())
                .provider("LOCAL")
                .role("ROLE_USER")
                .build();
        user = userRepo.save(user);
    }

    @Test
    @DisplayName("API Rate Limit: Should block requests exceeding the configured limit (e.g. 5 requests per min)")
    void testRateLimitOnCreatePost() throws Exception {
        String payload = """
                    {
                        "teamId": "LG",
                        "content": "Test Post",
                        "postType": "NORMAL"
                    }
                """;
        // The limit is 5 per 60 seconds. We'll mock the rateLimitService since Redis
        // isn't running.
        when(rateLimitService.isAllowed(anyString(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(true, true, true, true, true, false);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/cheer/posts")
                    .with(user(user.getId().toString()).roles("USER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.junit.jupiter.api.Assertions.assertNotEquals(429, status,
                                "Requests under limit should not be 429");
                    });
        }

        // The 6th request should be blocked by the Rate Limiter -> HTTP 429
        mockMvc.perform(post("/api/cheer/posts")
                .with(user(user.getId().toString()).roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andDo(print())
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Linked lookup permits 30 requests and rejects request 31 in its own bucket")
    void linkedLookup_usesIndependentThirtyRequestBucket() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        when(rateLimitService.isAllowed(anyString(), eq(30), eq(60), anyBoolean()))
                .thenAnswer(invocation -> attempts.incrementAndGet() <= 30);

        for (int i = 0; i < 30; i++) {
            mockMvc.perform(get("/api/cheer/posts/linked")
                    .with(user(user.getId().toString()).roles("USER"))
                    .param("diaryId", "999999"))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(429));
        }

        mockMvc.perform(get("/api/cheer/posts/linked")
                .with(user(user.getId().toString()).roles("USER"))
                .param("diaryId", "999999"))
                .andExpect(status().isTooManyRequests());

        org.mockito.ArgumentCaptor<String> keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(rateLimitService, atLeastOnce()).isAllowed(keyCaptor.capture(), eq(30), eq(60), anyBoolean());
        assertThat(keyCaptor.getAllValues())
                .allSatisfy(key -> {
                    assertThat(key).contains("cheer:linked");
                    assertThat(key).doesNotContain("create(");
                });
    }

    @Test
    @DisplayName("Public changes polling rejects over-budget requests before the feed service is called")
    void postChangesPolling_rejectsOverBudgetBeforeCallingFeedService() throws Exception {
        when(rateLimitService.isAllowed(anyString(), eq(60), eq(60), eq(true))).thenReturn(true, false);

        mockMvc.perform(get("/api/cheer/posts/changes").param("sinceId", "10"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/cheer/posts/changes").param("sinceId", "10"))
                .andExpect(status().isTooManyRequests());

        org.mockito.ArgumentCaptor<String> keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(rateLimitService, times(2)).isAllowed(keyCaptor.capture(), eq(60), eq(60), eq(true));
        assertThat(keyCaptor.getAllValues())
                .hasSize(2)
                .allSatisfy(key -> assertThat(key).contains("cheer:post_changes"));
        assertThat(keyCaptor.getAllValues().get(1)).isEqualTo(keyCaptor.getAllValues().get(0));
        verify(cheerService, times(1)).checkPostChanges(10L, null);
    }

    @Test
    @DisplayName("Changes polling uses separate buckets for anonymous IPs and authenticated users")
    void postChangesPolling_separatesAnonymousIpsAndAuthenticatedUser() throws Exception {
        when(rateLimitService.isAllowed(anyString(), eq(60), eq(60), eq(true))).thenReturn(true);

        for (String address : new String[] {"192.0.2.10", "192.0.2.10", "192.0.2.11"}) {
            mockMvc.perform(get("/api/cheer/posts/changes").param("sinceId", "10")
                    .with(request -> {
                        request.setRemoteAddr(address);
                        return request;
                    }))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/cheer/posts/changes").param("sinceId", "10")
                .with(user(user.getId().toString()).roles("USER"))
                .with(request -> {
                    request.setRemoteAddr("192.0.2.10");
                    return request;
                }))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<String> keys = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(rateLimitService, times(4)).isAllowed(keys.capture(), eq(60), eq(60), eq(true));
        assertThat(keys.getAllValues().get(0)).endsWith(":192.0.2.10");
        assertThat(keys.getAllValues().get(1)).isEqualTo(keys.getAllValues().get(0));
        assertThat(keys.getAllValues().get(2)).endsWith(":192.0.2.11");
        assertThat(keys.getAllValues().get(3)).endsWith(":" + user.getId());
        assertThat(keys.getAllValues().get(3)).isNotEqualTo(keys.getAllValues().get(0));
    }
}
