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
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        "spring.datasource.url=jdbc:h2:mem:login_signup_flow;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=false",
        // 기본값은 false 이므로 인증 강제 흐름을 검증하려면 명시적으로 켠다.
        "app.auth.email-verification.required=true",
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

    @MockitoBean
    private EmailService emailService;

    @Test
    @DisplayName("signup 후 이메일 인증 전 로그인은 403 EMAIL_NOT_VERIFIED, 인증 후 첫 로그인은 200과 쿠키를 반환하고 같은 날 재로그인에서도 포인트를 중복 적립하지 않는다")
    void signupThenLoginReturnsCookiesAndAwardsDailyBonusOnce() throws Exception {
        when(rateLimitService.isAllowed(anyString(), anyInt(), anyInt(), anyBoolean())).thenReturn(true);

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String createdEmail = "signup-login-" + suffix + "@example.com";
        String handle = "@Flow" + suffix.substring(0, 6);
        String password = "TestPassword123!";

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
                .andExpect(cookie().doesNotExist("Authorization"))
                .andExpect(cookie().doesNotExist("Refresh"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.emailVerificationRequired").value(true))
                .andExpect(jsonPath("$.data.email").value(createdEmail));

        verify(emailService).sendSignupVerificationEmail(eq(createdEmail), anyString(), anyLong());
        assertThat(userRepository.findByEmail(createdEmail).orElseThrow().isEmailVerified()).isFalse();

        // 같은 이메일 + provider/providerId 로 /signup 을 다시 호출해도 기존 계정에 연결하거나
        // 인증 메일을 다시 보내지 않는다(재발송 레이트리밋 우회 차단).
        Map<String, Object> relinkPayload = new java.util.HashMap<>(objectMapper.readValue(signupPayload, Map.class));
        relinkPayload.put("handle", "@Re" + suffix.substring(0, 6));
        relinkPayload.put("provider", "KAKAO");
        relinkPayload.put("providerId", "kakao-" + suffix);
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.USER_AGENT, "JUnit")
                        .content(objectMapper.writeValueAsString(relinkPayload)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
        verify(emailService, times(1)).sendSignupVerificationEmail(eq(createdEmail), anyString(), anyLong());
        assertThat(userRepository.findByEmail(createdEmail).orElseThrow().getProvider()).isEqualTo("LOCAL");
        assertThat(userRepository.findByEmail(createdEmail).orElseThrow().getProviderId()).isNull();

        String loginPayload = objectMapper.writeValueAsString(Map.of(
                "email", createdEmail,
                "password", password
        ));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.USER_AGENT, "JUnit")
                        .content(loginPayload))
                .andExpect(status().isForbidden())
                .andExpect(cookie().doesNotExist("Authorization"))
                .andExpect(cookie().doesNotExist("Refresh"))
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"))
                .andExpect(jsonPath("$.data.emailVerificationRequired").value(true));

        String unknownResendBody = mockMvc.perform(post("/api/auth/email-verification/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "nobody-" + suffix + "@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();

        // 실제 미인증 계정에 대한 재발송에서 메일 발송이 실패해도(SMTP/JobRunr) 없는 계정과 똑같은 200 응답이어야 한다.
        doThrow(new IllegalStateException("smtp down"))
                .when(emailService).sendSignupVerificationEmail(eq(createdEmail), anyString(), anyLong());
        String existingResendBody = mockMvc.perform(post("/api/auth/email-verification/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", createdEmail))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(existingResendBody).isEqualTo(unknownResendBody);

        // 재발송이 이전 토큰을 무효화하므로 가장 최근 발급된 토큰으로 인증한다.
        ArgumentCaptor<String> rawTokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(2)).sendSignupVerificationEmail(eq(createdEmail), rawTokenCaptor.capture(), anyLong());

        mockMvc.perform(post("/api/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", "not-a-real-token"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL_VERIFICATION_TOKEN"));

        String confirmPayload = objectMapper.writeValueAsString(Map.of("token", rawTokenCaptor.getAllValues().get(1)));
        mockMvc.perform(post("/api/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.verified").value(true));

        mockMvc.perform(post("/api/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_TOKEN_USED"));

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
        String password = "TestPassword123!";

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

        String duplicateEmailPayload = objectMapper.writeValueAsString(Map.of(
                "name", "Duplicate Email User",
                "handle", "@Dup" + suffix,
                "email", createdEmail.toUpperCase(),
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
                        .content(duplicateEmailPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/api/auth/check-handle")
                        .param("handle", " Fresh_" + suffix.substring(0, 4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.normalized").value("@fresh_" + suffix.substring(0, 4).toLowerCase()));
    }
}
