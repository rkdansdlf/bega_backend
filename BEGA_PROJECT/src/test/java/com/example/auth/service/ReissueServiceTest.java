package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.entity.RefreshToken;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.util.JWTUtil;
import com.example.common.exception.BadRequestBusinessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ReissueServiceTest {

    private final JWTUtil jwtUtil = mock(JWTUtil.class);
    private final RefreshRepository refreshRepository = mock(RefreshRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuthSessionService authSessionService = mock(AuthSessionService.class);
    private final AuthSecurityMonitoringService monitoringService = mock(AuthSecurityMonitoringService.class);
    private final RefreshTokenReuseDetector reuseDetector = mock(RefreshTokenReuseDetector.class);
    private final RefreshTokenRevocationService revocationService = mock(RefreshTokenRevocationService.class);
    private final ReissueService service = new ReissueService(
            jwtUtil,
            refreshRepository,
            userRepository,
            authSessionService,
            monitoringService,
            reuseDetector,
            revocationService);

    @BeforeEach
    void setUpMetadata() {
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
        when(refreshRepository.findAllByTokenForUpdate("refresh-token")).thenReturn(List.of(stored));
        when(jwtUtil.getUserId("refresh-token")).thenReturn(1L);
        when(jwtUtil.getTokenVersion("refresh-token")).thenReturn(0);
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user));
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
    }
}
