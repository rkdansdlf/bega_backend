package com.example.auth.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;

import com.example.auth.repository.UserRepository;
import com.example.auth.service.AuthSecurityMonitoringService;
import com.example.auth.service.TokenBlacklistService;
import com.example.auth.util.JWTUtil;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * JWTFilter 의 Origin/Referer(CSRF) 검사 범위 테스트.
 *
 * <p>쿠키 인증은 브라우저가 자동으로 쿠키를 붙이므로 Origin/Referer 가 없어도 차단하는 CSRF 방어를 유지한다.
 * Bearer 헤더만으로 인증하는 요청은 Origin/Referer 가 둘 다 없으면(모바일 앱, 서버 간 호출) 통과하지만,
 * 헤더가 하나라도 있으면 허용 목록과 대조한다.
 */
@ExtendWith(MockitoExtension.class)
class JWTFilterCsrfOriginTest {

    private static final String SECRET =
            "test-jwt-secret-64-characters-long-for-hs512-signature-tests-key-1234567890";
    private static final String ALLOWED_ORIGIN = "http://localhost:5176";
    private static final String EVIL_ORIGIN = "https://evil.example.com";

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthSecurityMonitoringService securityMonitoringService;

    private JWTUtil jwtUtil;
    private JWTFilter jwtFilter;

    @BeforeEach
    void setUp() {
        jwtUtil = new JWTUtil(SECRET, 1000L * 60 * 60 * 24 * 7);
        ReflectionTestUtils.setField(jwtUtil, "accessExpirationTime", 7_200_000L);
        jwtUtil.validateSecret();
        jwtFilter = new JWTFilter(jwtUtil, List.of(ALLOWED_ORIGIN), tokenBlacklistService,
                userRepository, securityMonitoringService);

        Mockito.lenient().when(tokenBlacklistService.isBlacklisted(anyString())).thenReturn(false);
        Mockito.lenient().when(userRepository.findUsableRoleByIdAndTokenVersion(Mockito.eq(1L), Mockito.any()))
                .thenReturn(Optional.of("ROLE_USER"));
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---- 쿠키 인증: CSRF 방어 유지 ----

    @Test
    @DisplayName("쿠키 인증 + 상태 변경 요청에 Origin/Referer 가 없으면 403")
    void cookieAuth_mutatingRequestWithoutOriginOrReferer_isForbidden() throws Exception {
        MockHttpServletRequest request = post();
        request.setCookies(new Cookie("Authorization", validToken()));

        Result result = run(request);

        assertThat(result.response.getStatus()).isEqualTo(403);
        assertThat(result.chainInvoked()).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        Mockito.verify(securityMonitoringService).recordInvalidOrigin();
    }

    @Test
    @DisplayName("쿠키 인증 + 허용되지 않은 Origin 이면 403")
    void cookieAuth_mutatingRequestFromDisallowedOrigin_isForbidden() throws Exception {
        MockHttpServletRequest request = post();
        request.setCookies(new Cookie("Authorization", validToken()));
        request.addHeader("Origin", EVIL_ORIGIN);

        Result result = run(request);

        assertThat(result.response.getStatus()).isEqualTo(403);
        assertThat(result.chainInvoked()).isFalse();
    }

    @Test
    @DisplayName("쿠키 인증 + 허용된 Origin 이면 인증된다")
    void cookieAuth_mutatingRequestFromAllowedOrigin_isAuthenticated() throws Exception {
        MockHttpServletRequest request = post();
        request.setCookies(new Cookie("Authorization", validToken()));
        request.addHeader("Origin", ALLOWED_ORIGIN);

        Result result = run(request);

        assertThat(result.chainInvoked()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("쿠키 인증 + Origin 은 없고 허용된 Referer 면 인증된다")
    void cookieAuth_mutatingRequestWithAllowedReferer_isAuthenticated() throws Exception {
        MockHttpServletRequest request = post();
        request.setCookies(new Cookie("Authorization", validToken()));
        request.addHeader("Referer", ALLOWED_ORIGIN + "/mypage");

        Result result = run(request);

        assertThat(result.chainInvoked()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("쿠키 인증 + 조회(GET) 요청은 Origin 이 없어도 인증된다")
    void cookieAuth_safeMethodWithoutOrigin_isAuthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/mypage");
        request.setCookies(new Cookie("Authorization", validToken()));

        Result result = run(request);

        assertThat(result.chainInvoked()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("쿠키와 Bearer 가 함께 오면 쿠키가 우선이라 CSRF 검사를 우회할 수 없다")
    void cookieAndBearerTogether_cookieWinsSoOriginCheckStillApplies() throws Exception {
        MockHttpServletRequest request = post();
        request.setCookies(new Cookie("Authorization", validToken()));
        request.addHeader("Authorization", "Bearer " + validToken());

        Result result = run(request);

        assertThat(result.response.getStatus()).isEqualTo(403);
        assertThat(result.chainInvoked()).isFalse();
    }

    @Test
    @DisplayName("만료된 쿠키와 유효한 Bearer 가 함께 오면 현재는 쿠키가 우선이라 401 (우선순위 고정)")
    void expiredCookieWithValidBearer_cookieTakesPrecedence() throws Exception {
        MockHttpServletRequest request = post();
        request.setCookies(new Cookie("Authorization", expiredToken()));
        request.addHeader("Origin", ALLOWED_ORIGIN);
        request.addHeader("Authorization", "Bearer " + validToken());

        Result result = run(request);

        assertThat(result.response.getStatus()).isEqualTo(401);
        assertThat(result.chainInvoked()).isFalse();
    }

    // ---- Bearer 전용 인증: Origin/Referer 불필요 ----

    @Test
    @DisplayName("Bearer 전용 + 상태 변경 요청은 Origin/Referer 가 모두 없으면(비브라우저 클라이언트) 인증된다")
    void bearerOnly_mutatingRequestWithoutOriginOrReferer_isAuthenticated() throws Exception {
        for (String method : List.of("POST", "PUT", "PATCH", "DELETE")) {
            SecurityContextHolder.clearContext();
            MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/mypage");
            request.addHeader("Authorization", "Bearer " + validToken());

            Result result = run(request);

            assertThat(result.chainInvoked()).as(method).isTrue();
            assertThat(result.response.getStatus()).as(method).isEqualTo(200);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).as(method).isNotNull();
        }
    }

    @Test
    @DisplayName("Bearer 전용이어도 허용되지 않은 Origin 이 명시되면 403")
    void bearerOnly_disallowedOrigin_isForbidden() throws Exception {
        MockHttpServletRequest request = post();
        request.addHeader("Authorization", "Bearer " + validToken());
        request.addHeader("Origin", EVIL_ORIGIN);

        Result result = run(request);

        assertThat(result.response.getStatus()).isEqualTo(403);
        assertThat(result.chainInvoked()).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Bearer 전용이어도 파싱할 수 없는 Origin(\"null\" 등)은 헤더가 있는 것이므로 403")
    void bearerOnly_opaqueNullOrigin_isForbidden() throws Exception {
        MockHttpServletRequest request = post();
        request.addHeader("Authorization", "Bearer " + validToken());
        request.addHeader("Origin", "null");

        Result result = run(request);

        assertThat(result.response.getStatus()).isEqualTo(403);
        assertThat(result.chainInvoked()).isFalse();
    }

    @Test
    @DisplayName("Bearer 전용 + 허용된 Origin 이면 인증된다")
    void bearerOnly_allowedOrigin_isAuthenticated() throws Exception {
        MockHttpServletRequest request = post();
        request.addHeader("Authorization", "Bearer " + validToken());
        request.addHeader("Origin", ALLOWED_ORIGIN);

        Result result = run(request);

        assertThat(result.chainInvoked()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("Bearer 전용이어도 유효하지 않은 토큰은 여전히 거절된다")
    void bearerOnly_expiredToken_isStillRejected() throws Exception {
        MockHttpServletRequest request = post();
        request.addHeader("Authorization", "Bearer " + expiredToken());

        Result result = run(request);

        assertThat(result.response.getStatus()).isEqualTo(401);
        assertThat(result.chainInvoked()).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // ---- helpers ----

    private MockHttpServletRequest post() {
        return new MockHttpServletRequest("POST", "/api/mypage");
    }

    private String validToken() {
        return jwtUtil.createJwt("user@test.com", "ROLE_USER", 1L, 60_000L);
    }

    private String expiredToken() {
        return jwtUtil.createJwt("user@test.com", "ROLE_USER", 1L, -60_000L);
    }

    private Result run(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        jwtFilter.doFilter(request, response, chain);
        return new Result(response, chain);
    }

    private record Result(MockHttpServletResponse response, MockFilterChain chain) {
        boolean chainInvoked() {
            return chain.getRequest() != null;
        }
    }
}
