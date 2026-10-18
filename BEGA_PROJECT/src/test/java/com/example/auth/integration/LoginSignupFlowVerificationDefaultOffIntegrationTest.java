package com.example.auth.integration;

import com.example.auth.repository.UserRepository;
import com.example.auth.service.AuthSecurityMonitoringService;
import com.example.auth.service.EmailService;
import com.example.common.ratelimit.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.jwt.secret=test-jwt-secret-64-characters-long-for-hs512-signature-tests-key-1234567890",
        "spring.jwt.refresh-expiration=86400000",
        "spring.datasource.url=jdbc:h2:mem:login_signup_flow_verification_off;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
@DisplayName("Signup to login flow with email verification left at its default (off)")
class LoginSignupFlowVerificationDefaultOffIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private RateLimitService rateLimitService;

    @MockitoBean
    private AuthSecurityMonitoringService authSecurityMonitoringService;

    @MockitoBean
    private EmailService emailService;

    @Test
    @DisplayName("app.auth.email-verification.required 기본값(false)에서는 인증 메일은 보내되 미인증 계정도 로그인할 수 있다")
    void defaultConfigDoesNotBlockLoginForUnverifiedSignup() throws Exception {
        when(rateLimitService.isAllowed(anyString(), anyInt(), anyInt(), anyBoolean())).thenReturn(true);

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String createdEmail = "signup-off-" + suffix + "@example.com";
        String password = "TestPassword123!";

        String signupPayload = objectMapper.writeValueAsString(Map.of(
                "name", "Signup Off User",
                "handle", "@Off" + suffix.substring(0, 6),
                "email", createdEmail,
                "password", password,
                "confirmPassword", password,
                "favoriteTeam", "없음",
                "policyConsents", List.of(
                        Map.of("policyType", "TERMS", "version", "2026-02-26", "agreed", true),
                        Map.of("policyType", "PRIVACY", "version", "2026-02-26", "agreed", true),
                        Map.of("policyType", "DATA_DISCLAIMER", "version", "2026-02-26", "agreed", true)
                )));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.USER_AGENT, "JUnit")
                        .content(signupPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.emailVerificationRequired").value(false))
                .andExpect(jsonPath("$.data.email").value(createdEmail));

        verify(emailService).sendSignupVerificationEmail(eq(createdEmail), anyString(), anyLong());
        assertThat(userRepository.findByEmail(createdEmail).orElseThrow().isEmailVerified()).isFalse();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.USER_AGENT, "JUnit")
                        .content(objectMapper.writeValueAsString(Map.of("email", createdEmail, "password", password))))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("Authorization"))
                .andExpect(jsonPath("$.success").value(true));
    }
}
