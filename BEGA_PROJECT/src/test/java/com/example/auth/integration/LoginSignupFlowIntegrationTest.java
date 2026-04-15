package com.example.auth.integration;

import com.example.auth.repository.UserRepository;
import com.example.auth.service.AuthSecurityMonitoringService;
import com.example.common.ratelimit.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.jwt.secret=test-jwt-secret-32-characters-long",
        "spring.jwt.refresh-expiration=86400000",
        "spring.datasource.url=jdbc:h2:mem:login_signup_flow;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
@DisplayName("Signup to login integration flow")
class LoginSignupFlowIntegrationTest {

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

    @Test
    @DisplayName("signup 직후 첫 로그인은 200과 쿠키를 반환하고 같은 날 재로그인에서도 포인트를 중복 적립하지 않는다")
    void signupThenLoginReturnsCookiesAndAwardsDailyBonusOnce() throws Exception {
        when(rateLimitService.isAllowed(anyString(), anyInt(), anyInt(), anyBoolean())).thenReturn(true);

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String createdEmail = "signup-login-" + suffix + "@example.com";
        String handle = "@Flow" + suffix.substring(0, 6);
        String password = "Test1234!";

        String signupPayload = objectMapper.writeValueAsString(Map.of(
                "name", "Signup Flow User",
                "handle", handle,
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
                .andExpect(jsonPath("$.success").value(true));

        String loginPayload = objectMapper.writeValueAsString(Map.of(
                "email", createdEmail,
                "password", password
        ));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.USER_AGENT, "JUnit")
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("Authorization"))
                .andExpect(cookie().exists("Refresh"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cheerPoints").value(5));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.USER_AGENT, "JUnit")
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("Authorization"))
                .andExpect(cookie().exists("Refresh"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cheerPoints").value(5));

        var persistedUser = userRepository.findByEmail(createdEmail).orElseThrow();
        assertThat(persistedUser.getCheerPoints()).isEqualTo(5);
        assertThat(persistedUser.getLastBonusDate()).isNotNull();
        assertThat(persistedUser.getLastLoginDate()).isNotNull();
        assertThat(persistedUser.getHandle()).isEqualTo(handle.toLowerCase());
    }

    @Test
    @DisplayName("회원가입 사전 중복확인은 handle과 email canonicalization을 반영한다")
    void signupAvailabilityChecksNormalizeInputs() throws Exception {
        when(rateLimitService.isAllowed(anyString(), anyInt(), anyInt(), anyBoolean())).thenReturn(true);

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String createdEmail = "signup-availability-" + suffix + "@example.com";
        String mixedCaseHandle = "@Slug" + suffix.substring(0, 4);
        String password = "Test1234!";

        String signupPayload = objectMapper.writeValueAsString(Map.of(
                "name", "Signup Availability User",
                "handle", mixedCaseHandle,
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
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/auth/check-handle")
                        .param("handle", mixedCaseHandle.toUpperCase()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HANDLE_UNAVAILABLE"))
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.normalized").value(mixedCaseHandle.toLowerCase()));

        mockMvc.perform(get("/api/auth/check-email")
                        .with(user("tester").roles("USER"))
                        .param("email", createdEmail.toUpperCase()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.normalized").value(createdEmail));

        mockMvc.perform(get("/api/auth/check-handle")
                        .param("handle", " Fresh_" + suffix.substring(0, 4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.normalized").value("@fresh_" + suffix.substring(0, 4).toLowerCase()));
    }
}
