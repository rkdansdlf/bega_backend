package com.example.auth.integration;

import com.example.auth.service.AuthSecurityMonitoringService;
import com.example.common.ratelimit.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.jwt.secret=test-jwt-secret-64-characters-long-for-hs512-signature-tests-key-1234567890",
        "spring.jwt.refresh-expiration=86400000",
        "spring.datasource.url=jdbc:h2:mem:auth_rate_limit;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
@DisplayName("Authentication rate limit integration tests")
class AuthRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RateLimitService rateLimitService;

    @MockitoBean
    private AuthSecurityMonitoringService authSecurityMonitoringService;

    @BeforeEach
    void setUp() {
        reset(rateLimitService, authSecurityMonitoringService);
    }

    @Test
    @DisplayName("password reset request returns 429 when auth rate limit rejects the call")
    void passwordResetRequestReturns429WhenRateLimitRejects() throws Exception {
        when(rateLimitService.isAllowed(anyString(), anyInt(), anyInt(), anyBoolean())).thenReturn(false);

        mockMvc.perform(post("/api/auth/password/reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"blocked@example.com\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("너무 많은 요청을 보냈습니다. 잠시 후 다시 시도해주세요."));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rateLimitService).isAllowed(keyCaptor.capture(), org.mockito.Mockito.eq(3), org.mockito.Mockito.eq(3600),
                org.mockito.Mockito.eq(true));
        assertThat(keyCaptor.getValue()).contains("auth:password-reset-request");
        assertThat(keyCaptor.getValue()).contains("/api/auth/password/reset/request");
        verifyNoMoreInteractions(rateLimitService);
        verify(authSecurityMonitoringService).recordAuthRateLimitReject();
    }

    @Test
    @DisplayName("login request is rate limited by filter only once")
    void loginRateLimitUsesFilterOnlyOnce() throws Exception {
        when(rateLimitService.isAllowed(anyString(), anyInt(), anyInt(), anyBoolean())).thenReturn(false);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"blocked@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rateLimitService).isAllowed(keyCaptor.capture(), org.mockito.Mockito.eq(3), org.mockito.Mockito.eq(60),
                org.mockito.Mockito.eq(true));
        assertThat(keyCaptor.getValue()).contains("auth:login");
        assertThat(keyCaptor.getValue()).contains("/api/auth/login");
        verifyNoMoreInteractions(rateLimitService);
        verify(authSecurityMonitoringService).recordAuthRateLimitReject();
    }

    @Test
    @DisplayName("signup request is rate limited by filter only once")
    void signupRateLimitUsesFilterOnlyOnce() throws Exception {
        when(rateLimitService.isAllowed(anyString(), anyInt(), anyInt(), anyBoolean())).thenReturn(false);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rateLimitService).isAllowed(keyCaptor.capture(), org.mockito.Mockito.eq(3), org.mockito.Mockito.eq(3600),
                org.mockito.Mockito.eq(true));
        assertThat(keyCaptor.getValue()).contains("auth:signup");
        assertThat(keyCaptor.getValue()).contains("/api/auth/signup");
        verifyNoMoreInteractions(rateLimitService);
        verify(authSecurityMonitoringService).recordAuthRateLimitReject();
    }

    @Test
    @DisplayName("untrusted X-Forwarded-For is ignored when building auth rate limit keys")
    void untrustedForwardedForIsIgnoredForAuthRateLimitKeys() throws Exception {
        when(rateLimitService.isAllowed(anyString(), anyInt(), anyInt(), anyBoolean())).thenReturn(true);

        mockMvc.perform(post("/api/auth/password/reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.10");
                            request.addHeader("X-Forwarded-For", "198.51.100.77");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rateLimitService).isAllowed(keyCaptor.capture(), org.mockito.Mockito.eq(3), org.mockito.Mockito.eq(3600),
                org.mockito.Mockito.eq(true));
        assertThat(keyCaptor.getValue()).contains("203.0.113.10");
        assertThat(keyCaptor.getValue()).doesNotContain("198.51.100.77");
        verifyNoMoreInteractions(rateLimitService);
    }
}
