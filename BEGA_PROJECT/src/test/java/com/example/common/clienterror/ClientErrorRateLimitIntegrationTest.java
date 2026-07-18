package com.example.common.clienterror;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.auth.service.AuthSecurityMonitoringService;
import com.example.common.ratelimit.RateLimitService;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.jwt.secret=test-jwt-secret-64-characters-long-for-hs512-signature-tests-key-1234567890",
        "spring.jwt.refresh-expiration=86400000",
        "spring.datasource.url=jdbc:h2:mem:client_error_rate_limit;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
        "oci.s3.region=ap-seoul-1"
})
@DisplayName("Client error rate limit integration tests")
class ClientErrorRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RateLimitService rateLimitService;

    @MockitoBean
    private ClientErrorLoggingService clientErrorLoggingService;

    @MockitoBean
    private AuthSecurityMonitoringService authSecurityMonitoringService;

    @BeforeEach
    void setUp() {
        reset(rateLimitService, clientErrorLoggingService, authSecurityMonitoringService);
    }

    @Test
    @DisplayName("unauthenticated client-error ingestion returns 429 after the configured IP threshold")
    void unauthenticatedClientErrorReturns429AfterConfiguredIpThreshold() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        when(rateLimitService.isAllowed(anyString(), eq(120), eq(60), eq(true)))
                .thenAnswer(invocation -> attempts.incrementAndGet() <= 120);

        for (int i = 1; i <= 120; i++) {
            mockMvc.perform(post("/api/client-errors")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(clientErrorPayload("evt-" + i))
                            .with(request -> {
                                request.setRemoteAddr("203.0.113.70");
                                return request;
                            }))
                    .andExpect(status().isAccepted());
        }

        mockMvc.perform(post("/api/client-errors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clientErrorPayload("evt-121"))
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.70");
                            return request;
                        }))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        verify(rateLimitService, times(121)).isAllowed(
                "rate:limit:telemetry:client-error:POST:/api/client-errors:203.0.113.70",
                120,
                60,
                true);
        verify(clientErrorLoggingService, times(120)).logClientError(any(), any());
        verify(authSecurityMonitoringService, never()).recordAuthRateLimitReject();
    }

    private String clientErrorPayload(String eventId) {
        return """
                {
                  "eventId": "%s",
                  "category": "api",
                  "message": "Request failed",
                  "statusCode": 500,
                  "route": "/mypage",
                  "timestamp": "2026-06-03T00:00:00Z",
                  "userId": 123
                }
                """.formatted(eventId);
    }
}
