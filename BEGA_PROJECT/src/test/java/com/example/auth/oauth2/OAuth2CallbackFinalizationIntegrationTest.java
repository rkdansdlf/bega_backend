package com.example.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserProviderRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.AuthSecurityMonitoringService;
import com.example.auth.service.AuthSessionService;
import com.example.auth.service.OAuth2StateService;
import com.example.auth.service.UserService;
import com.example.bega.auth.dto.OAuth2LinkStateData;
import com.example.auth.util.JWTUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@DataJpaTest
@Import(OAuth2CallbackFinalizationService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OAuth2CallbackFinalizationIntegrationTest {

    @Autowired
    private OAuth2CallbackFinalizationService finalizationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProviderRepository userProviderRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private OAuth2StateService oAuth2StateService;

    @MockitoBean
    private AuthSessionService authSessionService;

    @MockitoBean
    private AuthSecurityMonitoringService securityMonitoringService;

    @MockitoBean
    private JWTUtil jwtUtil;

    @BeforeEach
    void setUpTokenStubs() {
        lenient().when(jwtUtil.getAccessTokenExpirationTime()).thenReturn(900_000L);
        lenient().when(jwtUtil.getRefreshTokenExpirationTime()).thenReturn(604_800_000L);
        lenient().when(jwtUtil.createJwt(anyString(), anyString(), anyLong(), anyLong(), anyInt()))
                .thenReturn("synthetic-access-token");
    }

    @Test
    void failedRefreshSessionInsertRollsBackProviderProfileAndBonusChanges() {
        UserEntity user = saveUser("rollback");
        user.setProvider("google");
        user.setProfileImageUrl("https://lh3.googleusercontent.com/old-avatar");
        userRepository.saveAndFlush(user);
        when(oAuth2StateService.saveState(user.getId())).thenReturn("synthetic-state");
        when(authSessionService.issueRefreshToken(
                anyString(), anyString(), anyLong(), any(), any(HttpServletRequest.class)))
                .thenThrow(new IllegalStateException("synthetic refresh persistence failure"));

        assertThatThrownBy(() -> finalizationService.finalizeCallback(
                new OAuth2LoginIntent(
                        "google",
                        "provider-id-rollback",
                        user.getEmail(),
                        "Updated OAuth Name",
                        "https://lh3.googleusercontent.com/new-avatar",
                        true,
                        false,
                        new OAuth2LinkStateData(user.getId(), System.currentTimeMillis(), null)),
                new MockHttpServletRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("synthetic refresh persistence failure");

        UserEntity after = userRepository.findById(user.getId()).orElseThrow();
        assertThat(after.getName()).isEqualTo("rollback-user");
        assertThat(after.getProfileImageUrl()).isEqualTo("https://lh3.googleusercontent.com/old-avatar");
        assertThat(after.getCheerPoints()).isZero();
        assertThat(after.getLastBonusDate()).isNull();
        assertThat(userProviderRepository.findByUserIdAndProvider(user.getId(), "google")).isEmpty();
    }

    @Test
    void failedCallbackStateWriteRollsBackChangesAndNeverAttemptsRefreshSession() {
        UserEntity user = saveUser("state-failure");
        user.setProvider("google");
        user.setProfileImageUrl("https://lh3.googleusercontent.com/old-avatar");
        userRepository.saveAndFlush(user);
        when(oAuth2StateService.saveState(user.getId())).thenThrow(
                new OAuth2StateService.OAuth2StateStoreException(
                        OAuth2StateService.ERROR_CODE_STATE_STORE_UNAVAILABLE,
                        new IllegalStateException("synthetic Redis failure")));

        assertThatThrownBy(() -> finalizationService.finalizeCallback(
                new OAuth2LoginIntent(
                        "google",
                        "provider-id-state-failure",
                        user.getEmail(),
                        "Updated OAuth Name",
                        "https://lh3.googleusercontent.com/new-avatar",
                        true,
                        false,
                        new OAuth2LinkStateData(user.getId(), System.currentTimeMillis(), null)),
                new MockHttpServletRequest()))
                .isInstanceOf(OAuth2StateService.OAuth2StateStoreException.class);

        verify(authSessionService, never()).issueRefreshToken(
                anyString(), anyString(), anyLong(), any(), any(HttpServletRequest.class));
        UserEntity after = userRepository.findById(user.getId()).orElseThrow();
        assertThat(after.getProfileImageUrl()).isEqualTo("https://lh3.googleusercontent.com/old-avatar");
        assertThat(after.getCheerPoints()).isZero();
        assertThat(after.getLastBonusDate()).isNull();
        assertThat(userProviderRepository.findByUserIdAndProvider(user.getId(), "google")).isEmpty();
    }

    @Test
    void accountBecomingIneligibleAtStateBoundaryRollsBackAndSkipsRefreshSession() {
        UserEntity user = saveUser("eligibility-boundary");
        user.setProvider("google");
        user.setProfileImageUrl("https://lh3.googleusercontent.com/old-avatar");
        userRepository.saveAndFlush(user);
        doAnswer(invocation -> {
            UserEntity finalizedUser = invocation.getArgument(0);
            finalizedUser.setEnabled(false);
            return null;
        }).when(userService).checkAndApplyDailyLoginBonus(any(UserEntity.class));
        when(oAuth2StateService.saveState(user.getId())).thenReturn("synthetic-state");

        assertThatThrownBy(() -> finalizationService.finalizeCallback(
                new OAuth2LoginIntent(
                        "google",
                        "provider-id-eligibility-boundary",
                        user.getEmail(),
                        "Updated OAuth Name",
                        "https://lh3.googleusercontent.com/new-avatar",
                        true,
                        false,
                        new OAuth2LinkStateData(user.getId(), System.currentTimeMillis(), null)),
                new MockHttpServletRequest()))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception ->
                        assertThat(exception.getError().getErrorCode())
                                .isEqualTo("oauth2_account_not_eligible"));

        verify(authSessionService, never()).issueRefreshToken(
                anyString(), anyString(), anyLong(), any(), any(HttpServletRequest.class));
        UserEntity after = userRepository.findById(user.getId()).orElseThrow();
        assertThat(after.isEnabled()).isTrue();
        assertThat(after.getName()).isEqualTo("eligibility-boundary-user");
        assertThat(after.getProfileImageUrl()).isEqualTo("https://lh3.googleusercontent.com/old-avatar");
        assertThat(after.getCheerPoints()).isZero();
        assertThat(userProviderRepository.findByUserIdAndProvider(user.getId(), "google")).isEmpty();
    }

    @Test
    void verifiedNewOAuthAccountCreatesProviderBonusAndSessionInFinalizationFlow() {
        when(oAuth2StateService.saveState(anyLong())).thenReturn("new-account-state");
        when(authSessionService.issueRefreshToken(
                anyString(), anyString(), anyLong(), any(), any(HttpServletRequest.class)))
                .thenReturn("new-account-refresh-token");

        OAuth2CallbackFinalizationResult result = finalizationService.finalizeCallback(
                new OAuth2LoginIntent(
                        "google",
                        "new-provider-id",
                        "new-oauth-user@example.com",
                        "New OAuth User",
                        null,
                        true,
                        false,
                        null),
                new MockHttpServletRequest());

        assertThat(result.user().getId()).isNotNull();
        assertThat(result.userDto().getEmail()).isEqualTo("new-oauth-user@example.com");
        assertThat(result.stateId()).isEqualTo("new-account-state");
        assertThat(result.refreshToken()).isEqualTo("new-account-refresh-token");
        assertThat(result.user().getCheerPoints()).isEqualTo(5);
        assertThat(userProviderRepository.findByProviderAndProviderId("google", "new-provider-id"))
                .get()
                .extracting(provider -> provider.getUser().getId())
                .isEqualTo(result.user().getId());
    }

    @Test
    void accountStateUpdateWaitsUntilEligibleCallbackAndSessionCommitTogether() throws Exception {
        UserEntity user = saveUser("race");
        userProviderRepository.saveAndFlush(com.example.auth.entity.UserProvider.builder()
                .user(user)
                .provider("google")
                .providerId("provider-id-race")
                .email(user.getEmail())
                .build());

        CountDownLatch callbackHoldingUserLock = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);
        CountDownLatch stateUpdateStarted = new CountDownLatch(1);
        when(oAuth2StateService.saveState(user.getId())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            callbackHoldingUserLock.countDown();
            if (!releaseCallback.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("synthetic callback release timeout");
            }
            return "race-state";
        });
        when(authSessionService.issueRefreshToken(
                anyString(), anyString(), anyLong(), any(), any(HttpServletRequest.class)))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                    return "race-refresh-token";
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<OAuth2CallbackFinalizationResult> callback = executor.submit(() ->
                    finalizationService.finalizeCallback(
                            new OAuth2LoginIntent(
                                    "google",
                                    "provider-id-race",
                                    user.getEmail(),
                                    "race-user",
                                    null,
                                    true,
                                    false,
                                    null),
                            new MockHttpServletRequest()));

            assertThat(callbackHoldingUserLock.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> stateUpdate = executor.submit(() -> {
                stateUpdateStarted.countDown();
                TransactionTemplate tx = new TransactionTemplate(transactionManager);
                tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                tx.executeWithoutResult(status -> {
                    UserEntity current = userRepository.findByIdForWrite(user.getId()).orElseThrow();
                    current.setEnabled(false);
                    userRepository.saveAndFlush(current);
                });
            });
            assertThat(stateUpdateStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> stateUpdate.get(150, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseCallback.countDown();
            assertThat(callback.get(5, TimeUnit.SECONDS).refreshToken()).isEqualTo("race-refresh-token");
            stateUpdate.get(5, TimeUnit.SECONDS);
        } finally {
            releaseCallback.countDown();
            executor.shutdownNow();
        }

        UserEntity after = userRepository.findById(user.getId()).orElseThrow();
        assertThat(after.isEnabled()).isFalse();
        assertThat(after.getCheerPoints()).isEqualTo(5);
        assertThat(after.getLastBonusDate()).isNotNull();
    }

    private UserEntity saveUser(String suffix) {
        return userRepository.saveAndFlush(UserEntity.builder()
                .uniqueId(UUID.randomUUID())
                .handle("u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .name(suffix + "-user")
                .email("oauth-" + suffix + "-" + UUID.randomUUID() + "@example.com")
                .role("ROLE_USER")
                .enabled(true)
                .locked(false)
                .pendingDeletion(false)
                .cheerPoints(0)
                .build());
    }
}
