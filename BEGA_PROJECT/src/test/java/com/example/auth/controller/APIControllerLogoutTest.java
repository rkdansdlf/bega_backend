package com.example.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.entity.RefreshToken;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.service.AuthRegistrationService;
import com.example.auth.service.AuthSessionService;
import com.example.auth.service.OAuth2StateService;
import com.example.auth.service.PolicyConsentService;
import com.example.auth.service.TokenBlacklistService;
import com.example.auth.service.UserService;
import com.example.auth.util.AuthCookieUtil;
import com.example.auth.util.JWTUtil;
import com.example.common.dto.ApiResponse;
import com.example.common.web.ClientIpResolver;
import jakarta.servlet.http.Cookie;
import java.util.List;
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
    private RefreshRepository refreshRepository;

    @Mock
    private ClientIpResolver clientIpResolver;

    @Mock
    private AuthSessionService authSessionService;

    @Mock
    private JWTUtil jwtUtil;

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
                refreshRepository,
                new AuthCookieUtil(false),
                clientIpResolver,
                authSessionService);
        when(userService.getJWTUtil()).thenReturn(jwtUtil);
    }

    @Test
    @DisplayName("logout는 현재 세션이 해석되면 해당 refresh row만 삭제한다")
    void logout_deletesResolvedCurrentSessionOnly() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("Refresh", "refresh-current"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        RefreshToken currentSession = new RefreshToken();
        currentSession.setId(10L);
        currentSession.setSessionId("session-current");
        currentSession.setToken("db-refresh-current");

        RefreshToken otherSession = new RefreshToken();
        otherSession.setId(20L);
        otherSession.setSessionId("session-other");
        otherSession.setToken("db-refresh-other");

        when(jwtUtil.getEmail("refresh-current")).thenReturn("user@example.com");
        when(refreshRepository.findAllByEmailOrderByIdDesc("user@example.com"))
                .thenReturn(List.of(otherSession, currentSession));
        when(authSessionService.resolveCurrentSessionToken(List.of(otherSession, currentSession), request))
                .thenReturn(currentSession);

        ResponseEntity<ApiResponse> result = apiController.logout(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getHeaders().get(HttpHeaders.SET_COOKIE))
                .anyMatch(header -> header.startsWith("Authorization=") && header.contains("Max-Age=0"));
        assertThat(result.getHeaders().get(HttpHeaders.SET_COOKIE))
                .anyMatch(header -> header.startsWith("Refresh=") && header.contains("Max-Age=0"));
        verify(refreshRepository).delete(currentSession);
        verify(refreshRepository, never()).deleteAll(anyList());
    }

    @Test
    @DisplayName("logout는 현재 세션 해석에 실패하면 refresh token 값 기준 삭제로 폴백한다")
    void logout_fallsBackToTokenMatchWhenCurrentSessionIsNotResolved() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("Refresh", "refresh-current"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        RefreshToken matchedByToken = new RefreshToken();
        matchedByToken.setId(10L);
        matchedByToken.setToken("refresh-current");

        when(jwtUtil.getEmail("refresh-current")).thenReturn("user@example.com");
        when(refreshRepository.findAllByEmailOrderByIdDesc("user@example.com"))
                .thenReturn(List.of());
        when(authSessionService.resolveCurrentSessionToken(List.of(), request))
                .thenReturn(null);
        when(refreshRepository.findAllByToken("refresh-current"))
                .thenReturn(List.of(matchedByToken));

        ResponseEntity<ApiResponse> result = apiController.logout(request, response);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(refreshRepository).deleteAll(List.of(matchedByToken));
        verify(refreshRepository, never()).delete(matchedByToken);
    }
}
