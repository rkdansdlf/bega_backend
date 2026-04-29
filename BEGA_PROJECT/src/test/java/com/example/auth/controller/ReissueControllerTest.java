package com.example.auth.controller;

import com.example.auth.entity.RefreshToken;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.AuthSessionService;
import com.example.auth.service.AuthSessionMetadataResolver;
import com.example.auth.service.AuthSecurityMonitoringService;
import com.example.auth.service.RefreshTokenReuseDetector;
import com.example.auth.service.RefreshTokenRevocationService;
import com.example.auth.util.AuthCookieUtil;
import com.example.auth.util.JWTUtil;
import com.example.common.dto.ApiResponse;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.InvalidAuthorException;
import com.example.common.exception.RefreshTokenRevokeFailedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReissueControllerTest {

    @Mock
    private JWTUtil jwtUtil;

    @Mock
    private RefreshRepository refreshRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthSessionService authSessionService;

    @Mock
    private AuthSecurityMonitoringService authSecurityMonitoringService;

    @Mock
    private RefreshTokenReuseDetector refreshTokenReuseDetector;

    @Mock
    private RefreshTokenRevocationService refreshTokenRevocationService;

    private ReissueController createController() {
        AuthCookieUtil authCookieUtil = new AuthCookieUtil(false);
        lenient().when(authSessionService.resolveRequestMetadata(any())).thenReturn(
                new AuthSessionMetadataResolver.SessionMetadata(
                        "desktop",
                        "Desktop",
                        "Chrome",
                        "macOS",
                        "127.0.0.1",
                        java.time.LocalDateTime.now()));
        return new ReissueController(
                jwtUtil,
                refreshRepository,
                userRepository,
                authCookieUtil,
                authSessionService,
                authSecurityMonitoringService,
                refreshTokenReuseDetector,
                refreshTokenRevocationService);
    }

    @Test
    @DisplayName("리프레시 토큰이 없으면 예외를 던진다")
    void reissue_missingRefreshToken_throwsBadRequest() {
        ReissueController controller = createController();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authSessionService.extractRefreshToken(request)).thenReturn(null);

        assertThatThrownBy(() -> controller.reissue(request, response))
                .isInstanceOf(BadRequestBusinessException.class);

        verify(authSecurityMonitoringService).recordRefreshReissueReject("REFRESH_TOKEN_MISSING");
    }

    @Test
    @DisplayName("토큰 타입이 refresh가 아니면 예외를 던진다")
    void reissue_invalidTokenType_throwsBadRequest() {
        ReissueController controller = createController();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authSessionService.extractRefreshToken(request)).thenReturn("some-token");
        when(jwtUtil.getTokenType("some-token")).thenReturn("access");

        assertThatThrownBy(() -> controller.reissue(request, response))
                .isInstanceOf(BadRequestBusinessException.class);

        verify(authSecurityMonitoringService).recordRefreshReissueReject("INVALID_REFRESH_TOKEN_TYPE");
    }

    @Test
    @DisplayName("만료된 토큰이면 예외를 던진다")
    void reissue_expiredToken_throwsBadRequest() {
        ReissueController controller = createController();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authSessionService.extractRefreshToken(request)).thenReturn("expired-token");
        when(jwtUtil.getTokenType("expired-token")).thenReturn("refresh");
        when(jwtUtil.isExpired("expired-token")).thenReturn(true);
        when(refreshRepository.findAllByToken("expired-token")).thenReturn(List.of());

        assertThatThrownBy(() -> controller.reissue(request, response))
                .isInstanceOf(BadRequestBusinessException.class);

        verify(authSecurityMonitoringService).recordRefreshReissueReject("REFRESH_TOKEN_EXPIRED");
    }

    @Test
    @DisplayName("DB에 토큰이 없으면 예외를 던진다")
    void reissue_tokenNotInDb_throwsBadRequest() {
        ReissueController controller = createController();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authSessionService.extractRefreshToken(request)).thenReturn("valid-token");
        when(jwtUtil.getTokenType("valid-token")).thenReturn("refresh");
        when(jwtUtil.isExpired("valid-token")).thenReturn(false);
        when(refreshRepository.findAllByToken("valid-token")).thenReturn(List.of());
        when(refreshTokenReuseDetector.findReuseUserId("valid-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.reissue(request, response))
                .isInstanceOf(BadRequestBusinessException.class);

        verify(authSecurityMonitoringService).recordRefreshReissueReject("REFRESH_TOKEN_NOT_FOUND");
    }

    @Test
    @DisplayName("재사용 토큰이면 세션 폐기 후 보안 경고 예외를 던진다")
    void reissue_reusedToken_revokesSessionsThenThrowsSecurityWarning() {
        ReissueController controller = createController();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(authSessionService.extractRefreshToken(request)).thenReturn("reused-token");
        when(jwtUtil.getTokenType("reused-token")).thenReturn("refresh");
        when(jwtUtil.isExpired("reused-token")).thenReturn(false);
        when(refreshRepository.findAllByToken("reused-token")).thenReturn(List.of());
        when(refreshTokenReuseDetector.findReuseUserId("reused-token")).thenReturn(Optional.of(42L));
        when(refreshTokenRevocationService.revokeAllSessionsAfterReuse(42L))
                .thenReturn(new RefreshTokenRevocationService.RevokedRefreshSessions(42L, "user@test.com"));

        assertThatThrownBy(() -> controller.reissue(request, response))
                .isInstanceOfSatisfying(BadRequestBusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("REFRESH_TOKEN_REUSE_DETECTED");
                    assertThat(ex.getMessage()).contains("모든 세션이 종료되었습니다");
                });

        verify(refreshTokenRevocationService).revokeAllSessionsAfterReuse(42L);
        verify(authSecurityMonitoringService).recordRefreshReissueReject("REFRESH_TOKEN_REUSE_DETECTED");
    }

    @Test
    @DisplayName("재사용 토큰 세션 폐기 실패는 503 fail-closed 예외를 던진다")
    void reissue_reusedTokenRevocationFailure_throwsServiceUnavailable() {
        ReissueController controller = createController();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(authSessionService.extractRefreshToken(request)).thenReturn("reused-token");
        when(jwtUtil.getTokenType("reused-token")).thenReturn("refresh");
        when(jwtUtil.isExpired("reused-token")).thenReturn(false);
        when(refreshRepository.findAllByToken("reused-token")).thenReturn(List.of());
        when(refreshTokenReuseDetector.findReuseUserId("reused-token")).thenReturn(Optional.of(42L));
        when(refreshTokenRevocationService.revokeAllSessionsAfterReuse(42L))
                .thenThrow(new RefreshTokenRevokeFailedException());

        assertThatThrownBy(() -> controller.reissue(request, response))
                .isInstanceOfSatisfying(RefreshTokenRevokeFailedException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(503);
                    assertThat(ex.getCode()).isEqualTo("REFRESH_TOKEN_REVOKE_FAILED");
                    assertThat(ex.getMessage()).doesNotContain("모든 세션이 종료되었습니다");
                });

        verify(refreshTokenRevocationService).revokeAllSessionsAfterReuse(42L);
        verify(authSecurityMonitoringService).recordRefreshReissueReject("REFRESH_TOKEN_REVOKE_FAILED");
        verify(authSecurityMonitoringService, never()).recordRefreshReissueReject("REFRESH_TOKEN_REUSE_DETECTED");
    }

    @Test
    @DisplayName("비활성 유저면 예외를 던진다")
    void reissue_disabledUser_throwsUnauthorized() {
        ReissueController controller = createController();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        RefreshToken rt = mock(RefreshToken.class);
        when(rt.getEmail()).thenReturn("test@test.com");

        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(42L);
        when(user.isEnabled()).thenReturn(false);

        when(authSessionService.extractRefreshToken(request)).thenReturn("valid-token");
        when(jwtUtil.getTokenType("valid-token")).thenReturn("refresh");
        when(jwtUtil.isExpired("valid-token")).thenReturn(false);
        when(jwtUtil.getUserId("valid-token")).thenReturn(42L);
        when(jwtUtil.getTokenVersion("valid-token")).thenReturn(0);
        when(refreshRepository.findAllByToken("valid-token")).thenReturn(List.of(rt));
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> controller.reissue(request, response))
                .isInstanceOf(InvalidAuthorException.class);
    }

    @Test
    @DisplayName("정상 재발급 시 200을 반환하고 쿠키를 설정한다")
    void reissue_happyPath_setsNewCookies() {
        ReissueController controller = createController();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        RefreshToken rt = mock(RefreshToken.class);
        when(rt.getEmail()).thenReturn("test@test.com");

        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(42L);
        when(user.getEmail()).thenReturn("test@test.com");
        when(user.getRole()).thenReturn("ROLE_USER");
        when(user.isEnabled()).thenReturn(true);
        when(user.isLocked()).thenReturn(false);
        when(user.getTokenVersion()).thenReturn(0);

        when(authSessionService.extractRefreshToken(request)).thenReturn("valid-token");
        when(jwtUtil.getTokenType("valid-token")).thenReturn("refresh");
        when(jwtUtil.isExpired("valid-token")).thenReturn(false);
        when(jwtUtil.getUserId("valid-token")).thenReturn(42L);
        when(jwtUtil.getTokenVersion("valid-token")).thenReturn(0);
        when(jwtUtil.getSessionId("valid-token")).thenReturn("session123");
        when(jwtUtil.getAccessTokenExpirationTime()).thenReturn(7200000L);
        when(jwtUtil.getRefreshTokenExpirationTime()).thenReturn(86400000L);
        when(jwtUtil.createJwt("test@test.com", "ROLE_USER", 42L, 7200000L, 0)).thenReturn("new-access");
        when(refreshRepository.findAllByToken("valid-token")).thenReturn(List.of(rt));
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        AuthSessionService.IssuedRefreshSession issued = new AuthSessionService.IssuedRefreshSession("new-refresh", "sess");
        when(authSessionService.rotateRefreshSession(eq(rt), eq("test@test.com"), eq("ROLE_USER"),
                eq(42L), eq(0), eq(request), eq("session123"))).thenReturn(issued);

        ResponseEntity<ApiResponse> result = controller.reissue(request, response);

        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(response.getHeaders("Set-Cookie")).isNotEmpty();
    }
}
