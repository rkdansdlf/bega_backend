package com.example.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.example.auth.service.AuthRegistrationService;
import com.example.auth.service.AuthSessionLogoutService;
import com.example.auth.service.OAuth2StateService;
import com.example.auth.service.PolicyConsentService;
import com.example.auth.service.TokenBlacklistService;
import com.example.auth.security.CookieMutationOriginValidator;
import com.example.auth.security.CookieCsrfTokenService;
import com.example.auth.service.UserService;
import com.example.auth.util.AuthCookieUtil;
import com.example.auth.util.JWTUtil;
import com.example.common.dto.ApiResponse;
import com.example.common.web.ClientIpResolver;
import jakarta.servlet.http.Cookie;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class APIControllerLogoutTest {

    @Mock
    private UserService userService;

    @Mock
    private AuthRegistrationService authRegistrationService;

    @Mock
    private PolicyConsentService policyConsentService;

    @Mock
    private OAuth2StateService oAuth2StateService;

    @Mock
    private com.example.bega.auth.service.OAuth2LinkStateService oAuth2LinkStateService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private ClientIpResolver clientIpResolver;

    @Mock
    private JWTUtil jwtUtil;

    @Mock
    private AuthSessionLogoutService authSessionLogoutService;

    @Mock
    private CookieMutationOriginValidator cookieMutationOriginValidator;

    @Mock
    private CookieCsrfTokenService cookieCsrfTokenService;

    private APIController apiController;

    @BeforeEach
    void setUp() {
        apiController = new APIController(
                userService,
                authRegistrationService,
                policyConsentService,
                oAuth2StateService,
                oAuth2LinkStateService,
                tokenBlacklistService,
                authSessionLogoutService,
                new AuthCookieUtil(false),
                clientIpResolver,
                cookieMutationOriginValidator,
                cookieCsrfTokenService);
        lenient().when(userService.getJWTUtil()).thenReturn(jwtUtil);
    }

    @Test
    @DisplayName("logout는 refresh cookie를 세션 revocation 서비스에 위임한다")
    void logoutDelegatesRefreshCookieToSessionRevocationService() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("Refresh", "refresh-current"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        ResponseEntity<ApiResponse<Void>> result = apiController.logout(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getHeaders().get(HttpHeaders.SET_COOKIE))
                .anyMatch(header -> header.startsWith("Authorization=") && header.contains("Max-Age=0"));
        assertThat(result.getHeaders().get(HttpHeaders.SET_COOKIE))
                .anyMatch(header -> header.startsWith("Refresh=") && header.contains("Max-Age=0"));
        assertThat(result.getHeaders().get(HttpHeaders.SET_COOKIE))
                .anyMatch(header -> header.startsWith("XSRF-TOKEN=") && header.contains("Max-Age=0"));
        verify(cookieCsrfTokenService).validate(request);
        verify(authSessionLogoutService).revoke("refresh-current", null);
    }

    @Test
    @DisplayName("refresh cookie가 없으면 기존 계정 단위 fallback을 유지한다")
    void logoutPreservesAccountFallbackWhenRefreshCookieIsMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("Authorization", "access-current"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtUtil.getUserId("access-current")).thenReturn(10L);
        when(jwtUtil.getExpiration("access-current")).thenReturn(new Date(System.currentTimeMillis() + 60_000L));

        ResponseEntity<ApiResponse<Void>> result = apiController.logout(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(authSessionLogoutService).revoke(null, 10L);
    }
}
