package com.example.cheerboard.integration;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.common.ratelimit.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=6379",
        "spring.data.redis.repositories.enabled=false",
        "storage.type=oci",
        "oci.s3.endpoint=http://localhost:4566",
        "oci.s3.access-key=test-access-key",
        "oci.s3.secret-key=test-secret-key",
        "oci.s3.bucket=test-bucket",
        "oci.s3.region=ap-seoul-1",
        "spring.autoconfigure.exclude=io.awspring.cloud.autoconfigure.s3.S3AutoConfiguration"
})
class CheerRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private com.example.kbo.repository.TeamRepository teamRepo;

    @MockBean
    private RateLimitService rateLimitService;

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
        when(rateLimitService.isAllowed(anyString(), anyInt(), anyInt()))
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
}
