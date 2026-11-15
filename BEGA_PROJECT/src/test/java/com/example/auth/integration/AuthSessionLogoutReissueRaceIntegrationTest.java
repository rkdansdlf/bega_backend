package com.example.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.auth.controller.APIController;
import com.example.auth.entity.RefreshToken;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.security.CookieCsrfTokenService;
import com.example.auth.security.CookieMutationOriginValidator;
import com.example.auth.service.AuthRegistrationService;
import com.example.auth.service.AuthSecurityMonitoringService;
import com.example.auth.service.AuthSessionLogoutService;
import com.example.auth.service.AuthSessionMetadataResolver;
import com.example.auth.service.AuthSessionService;
import com.example.auth.service.OAuth2StateService;
import com.example.auth.service.PolicyConsentService;
import com.example.auth.service.RefreshTokenDigestService;
import com.example.auth.service.RefreshTokenRevocationService;
import com.example.auth.service.RefreshTokenReuseDetector;
import com.example.auth.service.ReissueService;
import com.example.auth.service.ReissuedTokens;
import com.example.auth.service.TokenBlacklistService;
import com.example.auth.service.UserService;
import com.example.auth.util.AuthCookieUtil;
import com.example.auth.util.JWTUtil;
import com.example.bega.auth.service.OAuth2LinkStateService;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.web.ClientIpResolver;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DataJpaTest
@Import({
        AuthSessionLogoutService.class,
        AuthSessionService.class,
        RefreshTokenRevocationService.class,
        ReissueService.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthSessionLogoutReissueRaceIntegrationTest {

    @Autowired private AuthSessionLogoutService logoutService;
    @Autowired private ReissueService reissueService;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshRepository refreshRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean private JWTUtil jwtUtil;
    @MockitoBean private RefreshTokenDigestService digestService;
    @MockitoBean private RefreshTokenReuseDetector reuseDetector;
    @MockitoBean private AuthSessionMetadataResolver metadataResolver;
    @MockitoBean private AuthSecurityMonitoringService monitoringService;

    @Test
    void reissueWinsThenDelayedLegacyLogoutRevokesOnlyThatSession() throws Exception {
        Fixture fixture = createFixture(true);
        AtomicReference<RefreshTokenReuseDetector.ReuseMarker> marker = new AtomicReference<>();
        configureLegacyRefresh(fixture, marker);

        CountDownLatch reachedTokenDelivery = new CountDownLatch(1);
        CountDownLatch allowReissueCommit = new CountDownLatch(1);
        CountDownLatch logoutStarted = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            MockHttpServletRequest request = refreshRequest(fixture.oldToken());
            Future<ReissuedTokens> reissue = workers.submit(() -> reissueService.reissue(
                    fixture.oldToken(),
                    request,
                    tokens -> {
                        reachedTokenDelivery.countDown();
                        await(allowReissueCommit);
                    }));

            assertTrue(reachedTokenDelivery.await(5, TimeUnit.SECONDS), "rotation should reach response delivery");
            Future<?> logout = workers.submit(() -> {
                logoutStarted.countDown();
                logoutThroughController(fixture.oldToken());
            });
            assertTrue(logoutStarted.await(5, TimeUnit.SECONDS), "logout should start while reissue holds the owner lock");
            assertThrows(TimeoutException.class, () -> logout.get(200, TimeUnit.MILLISECONDS));

            allowReissueCommit.countDown();
            assertThat(reissue.get(5, TimeUnit.SECONDS).refreshToken()).isEqualTo(fixture.newToken());
            logout.get(5, TimeUnit.SECONDS);

            List<RefreshToken> remaining = refreshRepository.findAllByEmail(fixture.email());
            assertThat(remaining).hasSize(1);
            assertThat(remaining.getFirst().getTokenDigest()).isEqualTo(fixture.otherTokenDigest());
            assertThat(remaining.getFirst().getSessionId()).isEqualTo(fixture.otherSessionId());
        } finally {
            allowReissueCommit.countDown();
            workers.shutdownNow();
            workers.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void logoutWinsThenWaitingReissueRejectsTheDeletedRefreshRow() throws Exception {
        Fixture fixture = createFixture(false);
        AtomicReference<RefreshTokenReuseDetector.ReuseMarker> marker = new AtomicReference<>();
        configureLegacyRefresh(fixture, marker);
        CountDownLatch logoutDeletedRowInTransaction = new CountDownLatch(1);
        CountDownLatch allowLogoutCommit = new CountDownLatch(1);
        CountDownLatch reissueStarted = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            Future<?> logout = workers.submit(() -> transaction.execute(status -> {
                logoutThroughController(fixture.oldToken());
                logoutDeletedRowInTransaction.countDown();
                await(allowLogoutCommit);
                return null;
            }));
            assertTrue(logoutDeletedRowInTransaction.await(5, TimeUnit.SECONDS), "logout should hold the owner lock");

            Future<Throwable> reissue = workers.submit(() -> {
                reissueStarted.countDown();
                try {
                    reissueService.reissue(
                            fixture.oldToken(), refreshRequest(fixture.oldToken()), tokens -> { });
                    return null;
                } catch (Throwable throwable) {
                    return throwable;
                }
            });
            assertTrue(reissueStarted.await(5, TimeUnit.SECONDS), "reissue should begin behind logout");
            assertThrows(TimeoutException.class, () -> reissue.get(200, TimeUnit.MILLISECONDS));

            allowLogoutCommit.countDown();
            logout.get(5, TimeUnit.SECONDS);
            Throwable failure = reissue.get(5, TimeUnit.SECONDS);
            assertThat(failure)
                    .isInstanceOf(BadRequestBusinessException.class)
                    .extracting(error -> ((BadRequestBusinessException) error).getCode())
                    .isEqualTo("REFRESH_TOKEN_NOT_FOUND");
            assertThat(refreshRepository.findAllByEmail(fixture.email())).isEmpty();
        } finally {
            allowLogoutCommit.countDown();
            workers.shutdownNow();
            workers.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void replayedRotatedRefreshCommitsSessionRevocationBeforeReturningReuseError() throws Exception {
        Fixture fixture = createFixture(true);
        AtomicReference<RefreshTokenReuseDetector.ReuseMarker> marker = new AtomicReference<>();
        configureLegacyRefresh(fixture, marker);
        MockHttpServletRequest request = refreshRequest(fixture.oldToken());

        reissueService.reissue(fixture.oldToken(), request, tokens -> { });
        assertThat(refreshRepository.findAllByEmail(fixture.email())).hasSize(2);
        org.mockito.Mockito.verify(reuseDetector).markRotated(
                eq(fixture.oldToken()),
                eq(fixture.userId()),
                eq(fixture.sessionId()),
                longThat(ttl -> ttl > 0 && ttl <= 60_000L));

        BadRequestBusinessException failure = assertThrows(
                BadRequestBusinessException.class,
                () -> reissueService.reissue(fixture.oldToken(), request, tokens -> { }));

        assertThat(failure.getCode()).isEqualTo("REFRESH_TOKEN_REUSE_DETECTED");
        List<RefreshToken> remaining = refreshRepository.findAllByEmail(fixture.email());
        assertThat(remaining).hasSize(1);
        assertThat(remaining.getFirst().getTokenDigest()).isEqualTo(fixture.otherTokenDigest());
        assertThat(remaining.getFirst().getSessionId()).isEqualTo(fixture.otherSessionId());
    }

    private Fixture createFixture(boolean includeOtherSession) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return new TransactionTemplate(transactionManager).execute(status -> {
            UserEntity user = userRepository.save(UserEntity.builder()
                    .uniqueId(UUID.randomUUID())
                    .handle("@" + suffix)
                    .name("Session race test")
                    .email("session-race+" + suffix + "@test.invalid")
                    .password("encoded-test-password")
                    .role("ROLE_USER")
                    .provider("LOCAL")
                    .enabled(true)
                    .locked(false)
                    .tokenVersion(0)
                    .build());

            String oldToken = "legacy-refresh-" + suffix;
            String sessionId = "session-" + suffix;
            RefreshToken current = refreshToken(user.getEmail(), oldToken, sessionId, "Current Device");
            refreshRepository.save(current);

            String otherDigest = "other-refresh-" + suffix;
            String otherSession = "other-session-" + suffix;
            if (includeOtherSession) {
                refreshRepository.save(refreshToken(user.getEmail(), otherDigest, otherSession, "Other Device"));
            }
            return new Fixture(user.getId(), user.getEmail(), oldToken, sessionId, "new-refresh-" + suffix,
                    otherDigest, otherSession);
        });
    }

    private void logoutThroughController(String refreshToken) {
        APIController controller = new APIController(
                mock(UserService.class),
                mock(AuthRegistrationService.class),
                mock(PolicyConsentService.class),
                mock(OAuth2StateService.class),
                mock(OAuth2LinkStateService.class),
                mock(TokenBlacklistService.class),
                logoutService,
                new AuthCookieUtil(false),
                mock(ClientIpResolver.class),
                mock(CookieMutationOriginValidator.class),
                mock(CookieCsrfTokenService.class));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("Refresh", refreshToken));
        controller.logout(request, new MockHttpServletResponse());
    }

    private RefreshToken refreshToken(String email, String tokenDigest, String sessionId, String deviceLabel) {
        RefreshToken token = new RefreshToken();
        token.setEmail(email);
        token.setTokenDigest(tokenDigest);
        token.setSessionId(sessionId);
        token.setExpiryDate(LocalDateTime.now().plusDays(7));
        token.setDeviceType("desktop");
        token.setDeviceLabel(deviceLabel);
        token.setBrowser("Chrome");
        token.setOs("macOS");
        token.setIp("127.0.0.1");
        token.setLastSeenAt(LocalDateTime.now());
        return token;
    }

    private void configureLegacyRefresh(
            Fixture fixture,
            AtomicReference<RefreshTokenReuseDetector.ReuseMarker> marker) {
        AuthSessionMetadataResolver.SessionMetadata metadata = new AuthSessionMetadataResolver.SessionMetadata(
                "desktop", "Current Device", "Chrome", "macOS", "127.0.0.1", LocalDateTime.now());
        when(metadataResolver.resolve(any(jakarta.servlet.http.HttpServletRequest.class))).thenReturn(metadata);
        when(digestService.digest(any(String.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtil.getTokenType(fixture.oldToken())).thenReturn("refresh");
        when(jwtUtil.isExpired(fixture.oldToken())).thenReturn(false);
        when(jwtUtil.getUserId(fixture.oldToken())).thenReturn(fixture.userId());
        when(jwtUtil.getTokenVersion(fixture.oldToken())).thenReturn(0);
        when(jwtUtil.getSessionId(fixture.oldToken())).thenReturn(null);
        when(jwtUtil.getExpiration(fixture.oldToken())).thenReturn(new Date(System.currentTimeMillis() + 60_000L));
        when(jwtUtil.getAccessTokenExpirationTime()).thenReturn(60_000L);
        when(jwtUtil.getRefreshTokenExpirationTime()).thenReturn(604_800_000L);
        when(jwtUtil.createJwt(fixture.email(), "ROLE_USER", fixture.userId(), 60_000L, 0))
                .thenReturn("new-access-token");
        when(jwtUtil.createRefreshToken(fixture.email(), "ROLE_USER", fixture.userId(), 0, fixture.sessionId()))
                .thenReturn(fixture.newToken());
        doAnswer(invocation -> {
            marker.set(new RefreshTokenReuseDetector.ReuseMarker(
                    invocation.getArgument(1), invocation.getArgument(2)));
            return null;
        }).when(reuseDetector).markRotated(
                eq(fixture.oldToken()),
                eq(fixture.userId()),
                eq(fixture.sessionId()),
                anyLong());
        when(reuseDetector.findReuseMarker(fixture.oldToken()))
                .thenAnswer(invocation -> Optional.ofNullable(marker.get()));
    }

    private MockHttpServletRequest refreshRequest(String refreshToken) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("Refresh", refreshToken));
        return request;
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while holding synthetic auth session transaction");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while holding synthetic auth session transaction", exception);
        }
    }

    private record Fixture(
            Long userId,
            String email,
            String oldToken,
            String sessionId,
            String newToken,
            String otherTokenDigest,
            String otherSessionId) {
    }
}
