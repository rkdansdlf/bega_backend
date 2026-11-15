package com.example.auth.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;

import com.example.auth.entity.RefreshToken;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.util.JWTUtil;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.InvalidAuthorException;
import com.example.common.exception.RefreshTokenRevokeFailedException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

class ReissueServiceTest {

    private final JWTUtil jwtUtil = mock(JWTUtil.class);
    private final RefreshRepository refreshRepository = mock(RefreshRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuthSessionService authSessionService = mock(AuthSessionService.class);
    private final AuthSecurityMonitoringService monitoringService = mock(AuthSecurityMonitoringService.class);
    private final RefreshTokenReuseDetector reuseDetector = mock(RefreshTokenReuseDetector.class);
    private final RefreshTokenRevocationService revocationService = mock(RefreshTokenRevocationService.class);
    private final RefreshTokenDigestService refreshTokenDigestService = mock(RefreshTokenDigestService.class);
    private final ReissueService service = new ReissueService(
            jwtUtil,
            refreshRepository,
            userRepository,
            authSessionService,
            monitoringService,
            reuseDetector,
            revocationService,
            refreshTokenDigestService);

    @BeforeEach
    void setUpMetadata() {
        when(refreshTokenDigestService.digest(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(authSessionService.resolveRequestMetadata(any())).thenReturn(
                new AuthSessionMetadataResolver.SessionMetadata(
                        "desktop",
                        "Desktop",
                        "Chrome",
                        "macOS",
                        "127.0.0.1",
                        LocalDateTime.now()));
    }

    @Test
    void missingRefreshTokenPreservesRejectMetricAndError() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> service.reissue(null, request, tokens -> { }))
                .isInstanceOfSatisfying(BadRequestBusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("REFRESH_TOKEN_MISSING");
                    assertThat(exception.getMessage()).isEqualTo("Refresh Token이 없습니다.");
                });

        verify(monitoringService).recordRefreshReissueReject("REFRESH_TOKEN_MISSING");
    }

    @Test
    void rejectedRefreshDoesNotWriteEmailToSecurityLogs() {
        String refreshToken = "invalid-author-refresh-token";
        String email = "private-refresh@example.test";
        RefreshToken stored = new RefreshToken();
        stored.setEmail(email);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/auth/reissue");
        when(jwtUtil.getTokenType(refreshToken)).thenReturn("refresh");
        when(jwtUtil.isExpired(refreshToken)).thenReturn(false);
        when(refreshRepository.findAllByTokenDigestForUpdate(refreshToken)).thenReturn(List.of(stored));
        when(jwtUtil.getUserId(refreshToken)).thenReturn(1L);
        when(jwtUtil.getTokenVersion(refreshToken)).thenReturn(0);
        when(userRepository.findByIdForWrite(1L)).thenReturn(java.util.Optional.empty());

        Logger logger = (Logger) LoggerFactory.getLogger(ReissueService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThatThrownBy(() -> service.reissue(refreshToken, request, tokens -> { }))
                    .isInstanceOf(InvalidAuthorException.class);

            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .singleElement()
                    .asString()
                    .contains("security_event=REFRESH_REISSUE_REJECT", "email=[REDACTED]", "userId=1", "ip=127.0.0.1")
                    .doesNotContain(email);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void validRefreshTokenReturnsRotatedTokenValues() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RefreshToken stored = new RefreshToken();
        stored.setEmail("user@test.com");
        UserEntity user = UserEntity.builder()
                .id(1L)
                .email("user@test.com")
                .role("ROLE_USER")
                .enabled(true)
                .locked(false)
                .tokenVersion(0)
                .build();
        when(jwtUtil.getTokenType("refresh-token")).thenReturn("refresh");
        when(jwtUtil.isExpired("refresh-token")).thenReturn(false);
        when(refreshRepository.findAllByTokenDigestForUpdate("refresh-token")).thenReturn(List.of(stored));
        when(jwtUtil.getUserId("refresh-token")).thenReturn(1L);
        when(jwtUtil.getTokenVersion("refresh-token")).thenReturn(0);
        when(userRepository.findByIdForWrite(1L)).thenReturn(java.util.Optional.of(user));
        when(jwtUtil.getAccessTokenExpirationTime()).thenReturn(7_200_000L);
        when(jwtUtil.createJwt("user@test.com", "ROLE_USER", 1L, 7_200_000L, 0))
                .thenReturn("new-access");
        when(jwtUtil.getSessionId("refresh-token")).thenReturn("session-1");
        when(authSessionService.rotateRefreshSession(
                stored, "user@test.com", "ROLE_USER", 1L, 0, request, "session-1"))
                .thenReturn(new AuthSessionService.IssuedRefreshSession("new-refresh", "session-1"));
        when(jwtUtil.getRefreshTokenExpirationTime()).thenReturn(604_800_000L);

        AtomicReference<ReissuedTokens> delivered = new AtomicReference<>();
        ReissuedTokens result = service.reissue("refresh-token", request, delivered::set);

        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.accessTokenMaxAgeSeconds()).isEqualTo(7_200L);
        assertThat(result.refreshToken()).isEqualTo("new-refresh");
        assertThat(result.refreshTokenMaxAgeSeconds()).isEqualTo(604_800);
        assertThat(delivered.get()).isSameAs(result);

        var lockOrder = inOrder(userRepository, refreshRepository);
        lockOrder.verify(userRepository).findByIdForWrite(1L);
        lockOrder.verify(refreshRepository).findAllByTokenDigestForUpdate("refresh-token");
    }

    @Test
    void reissue_reusedRefreshTokenRevokesOnlyMappedSession() {
        String refreshToken = "replayed-refresh-token";
        UserEntity user = UserEntity.builder()
                .id(17L)
                .email("user@test.com")
                .role("ROLE_USER")
                .enabled(true)
                .locked(false)
                .tokenVersion(0)
                .build();
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(jwtUtil.getTokenType(refreshToken)).thenReturn("refresh");
        when(jwtUtil.isExpired(refreshToken)).thenReturn(false);
        when(jwtUtil.getUserId(refreshToken)).thenReturn(17L);
        when(jwtUtil.getTokenVersion(refreshToken)).thenReturn(0);
        when(userRepository.findByIdForWrite(17L)).thenReturn(java.util.Optional.of(user));
        when(refreshRepository.findAllByTokenDigestForUpdate(refreshToken)).thenReturn(List.of());
        when(reuseDetector.findReuseMarker(refreshToken)).thenReturn(java.util.Optional.of(
                new RefreshTokenReuseDetector.ReuseMarker(17L, "session-17")));
        when(revocationService.revokeSessionAfterReuse(17L, "session-17"))
                .thenReturn(new RefreshTokenRevocationService.RevokedRefreshSessions(17L, user.getEmail()));

        assertThatThrownBy(() -> service.reissue(refreshToken, request, tokens -> { }))
                .isInstanceOfSatisfying(BadRequestBusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("REFRESH_TOKEN_REUSE_DETECTED");
                    assertThat(exception.getMessage()).contains("해당 세션");
                });

        verify(revocationService).revokeSessionAfterReuse(17L, "session-17");
        verify(revocationService, never()).revokeAllSessionsAfterReuse(17L);
    }

    @Test
    void reissue_legacyReuseMarkerWithoutSessionDoesNotRevokeOtherSessions() {
        String refreshToken = "legacy-replayed-refresh-token";
        UserEntity user = UserEntity.builder()
                .id(18L)
                .email("user@test.com")
                .role("ROLE_USER")
                .enabled(true)
                .locked(false)
                .tokenVersion(0)
                .build();
        when(jwtUtil.getTokenType(refreshToken)).thenReturn("refresh");
        when(jwtUtil.isExpired(refreshToken)).thenReturn(false);
        when(jwtUtil.getUserId(refreshToken)).thenReturn(18L);
        when(jwtUtil.getTokenVersion(refreshToken)).thenReturn(0);
        when(userRepository.findByIdForWrite(18L)).thenReturn(java.util.Optional.of(user));
        when(refreshRepository.findAllByTokenDigestForUpdate(refreshToken)).thenReturn(List.of());
        when(reuseDetector.findReuseMarker(refreshToken)).thenReturn(java.util.Optional.of(
                new RefreshTokenReuseDetector.ReuseMarker(18L, null)));

        assertThatThrownBy(() -> service.reissue(refreshToken, new MockHttpServletRequest(), tokens -> { }))
                .isInstanceOf(RefreshTokenRevokeFailedException.class);

        verify(revocationService, never()).revokeSessionAfterReuse(any(), anyString());
        verify(revocationService, never()).revokeAllSessionsAfterReuse(18L);
    }

    @Test
    void reissueUsesSignedSessionClaimWhenReuseMarkerIsMissing() {
        String refreshToken = "signed-replayed-refresh-token";
        UserEntity user = UserEntity.builder()
                .id(19L)
                .email("user@test.com")
                .role("ROLE_USER")
                .enabled(true)
                .locked(false)
                .tokenVersion(0)
                .build();
        when(jwtUtil.getTokenType(refreshToken)).thenReturn("refresh");
        when(jwtUtil.isExpired(refreshToken)).thenReturn(false);
        when(jwtUtil.getUserId(refreshToken)).thenReturn(19L);
        when(jwtUtil.getTokenVersion(refreshToken)).thenReturn(0);
        when(jwtUtil.getSessionId(refreshToken)).thenReturn("signed-session-19");
        when(userRepository.findByIdForWrite(19L)).thenReturn(java.util.Optional.of(user));
        when(refreshRepository.findAllByTokenDigestForUpdate(refreshToken)).thenReturn(List.of());
        when(reuseDetector.findReuseMarker(refreshToken)).thenReturn(java.util.Optional.empty());
        when(revocationService.revokeSessionAfterReuse(19L, "signed-session-19"))
                .thenReturn(new RefreshTokenRevocationService.RevokedRefreshSessions(19L, user.getEmail()));

        assertThatThrownBy(() -> service.reissue(refreshToken, new MockHttpServletRequest(), tokens -> { }))
                .isInstanceOfSatisfying(BadRequestBusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("REFRESH_TOKEN_REUSE_DETECTED"));

        verify(revocationService).revokeSessionAfterReuse(19L, "signed-session-19");
        verify(revocationService, never()).revokeAllSessionsAfterReuse(19L);
    }
}
