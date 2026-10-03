package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import com.example.auth.entity.AccountDeletionToken;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.AccountDeletionTokenRepository;
import com.example.auth.repository.UserRepository;
import com.example.common.exception.BadRequestBusinessException;
import com.example.mate.service.PartyService;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import({AccountDeletionService.class, AccountDeletionFinalizationService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AccountDeletionFinalizationConcurrencyIntegrationTest {

    @Autowired
    private AccountDeletionService accountDeletionService;

    @Autowired
    private AccountDeletionFinalizationService accountDeletionFinalizationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountDeletionTokenRepository accountDeletionTokenRepository;

    @MockitoBean
    private PartyService partyService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private AccountSecurityService accountSecurityService;

    @Test
    void finalizerFailureRollsBackOnlyThatUserAndContinuesBatch() {
        // DB timestamps are microsecond precision; Linux clocks are nanosecond. Compare at the stored precision.
        LocalDateTime dueAt = LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.MICROS);
        UserEntity failedUser = savePendingUser(dueAt);
        UserEntity successfulUser = savePendingUser(dueAt);
        String failedToken = saveRecoveryToken(failedUser, dueAt);
        String successfulToken = saveRecoveryToken(successfulUser, dueAt);
        doThrow(new IllegalStateException("synthetic user-scoped failure"))
                .when(partyService).handleUserDeletion(failedUser.getId());

        accountDeletionService.finalizeDueDeletions();

        UserEntity failedAfter = userRepository.findById(failedUser.getId()).orElseThrow();
        UserEntity successfulAfter = userRepository.findById(successfulUser.getId()).orElseThrow();
        assertThat(failedAfter.getDeletionScheduledFor()).isEqualTo(dueAt);
        assertThat(successfulAfter.getDeletionScheduledFor()).isNull();
        assertThat(accountDeletionTokenRepository.findByTokenAndUser_Id(failedToken, failedUser.getId())).isPresent();
        assertThat(accountDeletionTokenRepository.findByTokenAndUser_Id(successfulToken, successfulUser.getId())).isEmpty();
    }

    @Test
    void recoveryCannotReviveAnAccountAfterFinalizerWinsTheUserLock() throws Exception {
        // DB timestamps are microsecond precision; Linux clocks are nanosecond. Compare at the stored precision.
        LocalDateTime dueAt = LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.MICROS);
        UserEntity user = savePendingUser(dueAt);
        String recoveryToken = saveRecoveryToken(user, dueAt);
        CountDownLatch finalizerHoldingUserLock = new CountDownLatch(1);
        CountDownLatch releaseFinalizer = new CountDownLatch(1);
        doAnswer(invocation -> {
            finalizerHoldingUserLock.countDown();
            if (!releaseFinalizer.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release synthetic finalizer");
            }
            return null;
        }).when(partyService).handleUserDeletion(user.getId());

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> finalization = workers.submit(() -> accountDeletionFinalizationService
                    .finalizeIfDue(user.getId(), LocalDateTime.now()));
            assertThat(finalizerHoldingUserLock.await(5, TimeUnit.SECONDS)).isTrue();

            Future<String> recovery = workers.submit(() -> {
                try {
                    accountDeletionService.recoverAccount(recoveryToken);
                    return "recovered";
                } catch (BadRequestBusinessException expected) {
                    return expected.getMessage();
                }
            });

            releaseFinalizer.countDown();
            assertThat(finalization.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(recovery.get(5, TimeUnit.SECONDS)).contains("유효하지 않은 복구 링크");
        } finally {
            releaseFinalizer.countDown();
            workers.shutdownNow();
        }

        UserEntity finalizedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(finalizedUser.isPendingDeletion()).isTrue();
        assertThat(finalizedUser.isEnabled()).isFalse();
        assertThat(finalizedUser.getDeletionScheduledFor()).isNull();
        assertThat(accountDeletionTokenRepository.findByTokenAndUser_Id(recoveryToken, user.getId())).isEmpty();
    }

    private UserEntity savePendingUser(LocalDateTime scheduledFor) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.saveAndFlush(UserEntity.builder()
                .uniqueId(UUID.randomUUID())
                .handle("@del" + suffix)
                .name("Deletion test")
                .email("deletion-" + suffix + "@example.test")
                .role("ROLE_USER")
                .enabled(false)
                .pendingDeletion(true)
                .deletionRequestedAt(scheduledFor.minusDays(7))
                .deletionScheduledFor(scheduledFor)
                .tokenVersion(1)
                .build());
    }

    private String saveRecoveryToken(UserEntity user, LocalDateTime expiryDate) {
        String token = "recovery-" + UUID.randomUUID();
        accountDeletionTokenRepository.saveAndFlush(AccountDeletionToken.builder()
                .token(token)
                .user(user)
                .expiryDate(expiryDate)
                .used(false)
                .build());
        return token;
    }
}
