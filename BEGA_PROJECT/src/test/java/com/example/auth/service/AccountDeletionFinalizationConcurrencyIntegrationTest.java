package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import com.example.admin.service.AdminUserDeletionPreparationService;
import com.example.auth.entity.AccountDeletionToken;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.AccountDeletionTokenRepository;
import com.example.auth.repository.UserRepository;
import com.example.common.exception.BadRequestBusinessException;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.CheerPostReport;
import com.example.cheerboard.domain.ReportReason;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.kbo.entity.TeamEntity;
import com.example.kbo.repository.TeamRepository;
import com.example.mate.dto.SellerPayoutProfileDTO;
import com.example.mate.entity.PaymentFlowType;
import com.example.mate.entity.PaymentStatus;
import com.example.mate.entity.PaymentTransaction;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PayoutTransaction;
import com.example.mate.entity.SellerPayoutProfile;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PaymentTransactionRepository;
import com.example.mate.repository.PayoutTransactionRepository;
import com.example.mate.repository.SellerPayoutProfileRepository;
import com.example.mate.service.PaymentMetricsService;
import com.example.mate.service.PayoutClaimService;
import com.example.mate.service.SellerRecoveryService;
import com.example.mate.service.SellerPayoutProfileService;
import com.example.mate.service.PartyService;
import com.example.media.service.MediaUploadService;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import({
        AdminUserDeletionPreparationService.class,
        AccountDeletionService.class,
        AccountDeletionFinalizationService.class,
        AccountDeletionFinancialService.class,
        AccountDeletionContentService.class,
        SellerPayoutProfileService.class,
        PayoutClaimService.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AccountDeletionFinalizationConcurrencyIntegrationTest {

    @Autowired
    private AccountDeletionService accountDeletionService;

    @Autowired
    private AccountDeletionFinalizationService accountDeletionFinalizationService;

    @Autowired
    private AdminUserDeletionPreparationService adminUserDeletionPreparationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SellerPayoutProfileRepository sellerPayoutProfileRepository;

    @Autowired
    private PartyApplicationRepository partyApplicationRepository;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private PayoutTransactionRepository payoutTransactionRepository;

    @Autowired
    private CheerPostRepo cheerPostRepository;

    @Autowired
    private CheerReportRepo cheerReportRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private SellerPayoutProfileService sellerPayoutProfileService;

    @Autowired
    private PayoutClaimService payoutClaimService;

    @Autowired
    private AccountDeletionTokenRepository accountDeletionTokenRepository;

    @MockitoBean
    private PartyService partyService;

    @MockitoBean
    private AccountDeletionIdentityService accountDeletionIdentityService;

    @MockitoBean
    private AccountDeletionCommunicationService accountDeletionCommunicationService;

    @MockitoBean
    private AccountDeletionReviewService accountDeletionReviewService;

    @MockitoBean
    private MediaUploadService mediaUploadService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private SellerRecoveryService sellerRecoveryService;

    @MockitoBean
    private PaymentMetricsService paymentMetricsService;

    @MockitoBean
    private AccountSecurityService accountSecurityService;

    @Test
    void repositoryCanReplaceTheRequiredAccountCreationTimestampWithoutChangingEntityMapping() {
        UserEntity user = savePendingUser(LocalDateTime.now().plusDays(1));
        LocalDateTime tombstoneTimestamp = LocalDateTime.of(1970, 1, 1, 0, 0);

        int updatedRows = userRepository.anonymizeCreatedAtById(user.getId(), tombstoneTimestamp);

        assertThat(updatedRows).isEqualTo(1);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getCreatedAt())
                .isEqualTo(tombstoneTimestamp);
    }

    @Test
    void failedAdminAnonymizationKeepsDueRetryMarkerForScheduledFinalizer() {
        UserEntity user = savePendingUser(LocalDateTime.now().plusDays(5));
        adminUserDeletionPreparationService.disableForDeletion(user.getId(), null);
        doThrow(new IllegalStateException("synthetic transient anonymizer failure"))
                .doNothing()
                .when(accountDeletionCommunicationService).anonymize(user.getId());

        assertThatThrownBy(() -> accountDeletionFinalizationService
                .finalizeIfDue(user.getId(), LocalDateTime.now().plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("synthetic transient anonymizer failure");

        UserEntity retryableUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(retryableUser.isEnabled()).isFalse();
        assertThat(retryableUser.isPendingDeletion()).isTrue();
        assertThat(retryableUser.getDeletionScheduledFor()).isNotNull()
                .isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(userRepository.findDuePendingDeletionUserIds(LocalDateTime.now()))
                .contains(user.getId());

        assertThat(accountDeletionFinalizationService
                .finalizeIfDue(user.getId(), LocalDateTime.now().plusSeconds(1))).isTrue();

        assertThat(userRepository.findDuePendingDeletionUserIds(LocalDateTime.now()))
                .doesNotContain(user.getId());
        org.mockito.Mockito.verify(accountDeletionCommunicationService, org.mockito.Mockito.times(2))
                .anonymize(user.getId());
    }

    @Test
    void finalizerFailureRollsBackOnlyThatUserAndContinuesBatch() {
        LocalDateTime dueAt = LocalDateTime.now().minusMinutes(1);
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
        LocalDateTime dueAt = LocalDateTime.now().minusMinutes(1);
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

    @Test
    void recoveryThatWinsUserLockCausesStaleFinalizerCandidateToBeSkipped() throws Exception {
        LocalDateTime scheduledFor = LocalDateTime.now().plusMinutes(1);
        UserEntity user = savePendingUser(scheduledFor);
        String recoveryToken = saveRecoveryToken(user, scheduledFor);
        CountDownLatch recoveryHoldingUserLock = new CountDownLatch(1);
        CountDownLatch releaseRecovery = new CountDownLatch(1);
        doAnswer(invocation -> {
            recoveryHoldingUserLock.countDown();
            if (!releaseRecovery.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release synthetic recovery");
            }
            return null;
        }).when(accountSecurityService).recordAccountDeletionCancelled(user.getId());

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> recovery = workers.submit(() -> accountDeletionService.recoverAccount(recoveryToken));
            assertThat(recoveryHoldingUserLock.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Boolean> finalization = workers.submit(() -> accountDeletionFinalizationService
                    .finalizeIfDue(user.getId(), scheduledFor.plusMinutes(1)));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> finalization.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseRecovery.countDown();
            recovery.get(5, TimeUnit.SECONDS);
            assertThat(finalization.get(5, TimeUnit.SECONDS)).isFalse();
        } finally {
            releaseRecovery.countDown();
            workers.shutdownNow();
        }

        UserEntity recoveredUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(recoveredUser.isPendingDeletion()).isFalse();
        assertThat(recoveredUser.isEnabled()).isTrue();
        assertThat(recoveredUser.getDeletionRequestedAt()).isNull();
        assertThat(recoveredUser.getDeletionScheduledFor()).isNull();
        assertThat(accountDeletionTokenRepository.findByTokenAndUser_Id(recoveryToken, user.getId())).isEmpty();
        org.mockito.Mockito.verify(partyService, org.mockito.Mockito.never()).handleUserDeletion(user.getId());
    }

    @Test
    void payoutProfileUpsertWaitsForFinalizerAndCannotRestoreScrubbedProfile() throws Exception {
        LocalDateTime dueAt = LocalDateTime.now().minusMinutes(1);
        UserEntity user = savePendingUser(dueAt);
        sellerPayoutProfileRepository.saveAndFlush(SellerPayoutProfile.builder()
                .userId(user.getId())
                .provider("TOSS")
                .providerSellerId("seller-before-finalization")
                .kycStatus("VERIFIED")
                .metadataJson("{\"source\":\"synthetic-test\"}")
                .build());

        CountDownLatch finalizerHoldingUserLock = new CountDownLatch(1);
        CountDownLatch releaseFinalizer = new CountDownLatch(1);
        CountDownLatch upsertStarted = new CountDownLatch(1);
        doAnswer(invocation -> {
            finalizerHoldingUserLock.countDown();
            if (!releaseFinalizer.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release synthetic finalizer");
            }
            return null;
        }).when(partyService).handleUserDeletion(user.getId());

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> finalization = workers.submit(
                    () -> accountDeletionFinalizationService.finalizeIfDue(user.getId(), LocalDateTime.now()));
            boolean finalizerPaused = finalizerHoldingUserLock.await(5, TimeUnit.SECONDS);
            if (!finalizerPaused && finalization.isDone()) {
                assertThat(finalization.get(1, TimeUnit.SECONDS))
                        .as("the due-account finalizer should complete after entering Party cleanup")
                        .isTrue();
            }
            assertThat(finalizerPaused)
                    .as("the finalizer should hold the account lock before entering Party cleanup")
                    .isTrue();

            SellerPayoutProfileDTO.UpsertRequest request = SellerPayoutProfileDTO.UpsertRequest.builder()
                    .userId(user.getId())
                    .provider("TOSS")
                    .providerSellerId("seller-after-finalization")
                    .kycStatus("VERIFIED")
                    .metadataJson("{\"source\":\"synthetic-race\"}")
                    .build();
            Future<String> upsert = workers.submit(() -> {
                upsertStarted.countDown();
                try {
                    sellerPayoutProfileService.upsert(request);
                    return "accepted";
                } catch (IllegalStateException expected) {
                    return expected.getMessage();
                }
            });

            assertThat(upsertStarted.await(5, TimeUnit.SECONDS)).isTrue();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> upsert.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseFinalizer.countDown();
            assertThat(finalization.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(upsert.get(5, TimeUnit.SECONDS)).contains("PAYOUT_PROFILE_ACCOUNT_UNAVAILABLE");
        } finally {
            releaseFinalizer.countDown();
            workers.shutdownNow();
        }

        SellerPayoutProfile finalizedProfile = sellerPayoutProfileRepository
                .findByUserIdAndProvider(user.getId(), "TOSS")
                .orElseThrow();
        assertThat(finalizedProfile.getProviderSellerId()).isNull();
        assertThat(finalizedProfile.getKycStatus()).isNull();
        assertThat(finalizedProfile.getMetadataJson()).isNull();
    }

    @Test
    void payoutProfileCreationWaitsForFinalizerWhenNoProfileExists() throws Exception {
        LocalDateTime dueAt = LocalDateTime.now().minusMinutes(1);
        UserEntity user = savePendingUser(dueAt);
        assertThat(sellerPayoutProfileRepository.findByUserIdAndProvider(user.getId(), "TOSS")).isEmpty();

        CountDownLatch finalizerHoldingUserLock = new CountDownLatch(1);
        CountDownLatch releaseFinalizer = new CountDownLatch(1);
        CountDownLatch upsertStarted = new CountDownLatch(1);
        doAnswer(invocation -> {
            finalizerHoldingUserLock.countDown();
            if (!releaseFinalizer.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release synthetic finalizer");
            }
            return null;
        }).when(partyService).handleUserDeletion(user.getId());

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> finalization = workers.submit(
                    () -> accountDeletionFinalizationService.finalizeIfDue(user.getId(), LocalDateTime.now()));
            assertThat(finalizerHoldingUserLock.await(5, TimeUnit.SECONDS)).isTrue();

            SellerPayoutProfileDTO.UpsertRequest request = SellerPayoutProfileDTO.UpsertRequest.builder()
                    .userId(user.getId())
                    .provider("TOSS")
                    .providerSellerId("seller-created-during-finalization")
                    .kycStatus("VERIFIED")
                    .metadataJson("{\"source\":\"synthetic-no-profile-race\"}")
                    .build();
            Future<String> upsert = workers.submit(() -> {
                upsertStarted.countDown();
                try {
                    sellerPayoutProfileService.upsert(request);
                    return "accepted";
                } catch (IllegalStateException expected) {
                    return expected.getMessage();
                }
            });

            assertThat(upsertStarted.await(5, TimeUnit.SECONDS)).isTrue();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> upsert.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseFinalizer.countDown();
            assertThat(finalization.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(upsert.get(5, TimeUnit.SECONDS)).contains("PAYOUT_PROFILE_ACCOUNT_UNAVAILABLE");
        } finally {
            releaseFinalizer.countDown();
            workers.shutdownNow();
        }

        assertThat(sellerPayoutProfileRepository.findByUserIdAndProvider(user.getId(), "TOSS")).isEmpty();
    }

    @Test
    void payoutClaimCommitsBeforeFinalizerAndRetainsTheRequiredFallback() throws Exception {
        LocalDateTime dueAt = LocalDateTime.now().minusMinutes(1);
        UserEntity seller = savePendingUser(dueAt);
        sellerPayoutProfileRepository.saveAndFlush(SellerPayoutProfile.builder()
                .userId(seller.getId())
                .provider("TOSS")
                .providerSellerId("seller-synthetic-claim-first")
                .kycStatus("VERIFIED")
                .metadataJson("{\"source\":\"synthetic-claim-first\"}")
                .build());
        PaymentTransaction payment = saveApprovedPaymentForSeller(seller);

        CountDownLatch claimHoldingSellerLock = new CountDownLatch(1);
        CountDownLatch releaseClaim = new CountDownLatch(1);
        doAnswer(invocation -> {
            claimHoldingSellerLock.countDown();
            if (!releaseClaim.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release synthetic payout claim");
            }
            return new SellerRecoveryService.RecoveryOffsetResult(0);
        }).when(sellerRecoveryService).reserveOffset(
                org.mockito.ArgumentMatchers.any(PayoutTransaction.class),
                org.mockito.ArgumentMatchers.eq(seller.getId()),
                org.mockito.ArgumentMatchers.eq(15_000));

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<PayoutClaimService.ClaimedPayout> claim = workers.submit(
                    () -> payoutClaimService.claimInitial(payment.getId(), true));
            assertThat(claimHoldingSellerLock.await(5, TimeUnit.SECONDS)).isTrue();

            CountDownLatch finalizerStarted = new CountDownLatch(1);
            Future<Boolean> finalization = workers.submit(() -> {
                finalizerStarted.countDown();
                return accountDeletionFinalizationService.finalizeIfDue(seller.getId(), LocalDateTime.now());
            });
            assertThat(finalizerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> finalization.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseClaim.countDown();
            assertThat(claim.get(5, TimeUnit.SECONDS).action())
                    .isEqualTo(PayoutClaimService.ClaimAction.CALL_PROVIDER);
            assertThat(finalization.get(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseClaim.countDown();
            workers.shutdownNow();
        }

        PayoutTransaction payout = payoutTransactionRepository
                .findTopByPaymentTransactionIdOrderByIdDesc(payment.getId())
                .orElseThrow();
        SellerPayoutProfile finalizedProfile = sellerPayoutProfileRepository
                .findByUserIdAndProvider(seller.getId(), "TOSS")
                .orElseThrow();
        assertThat(payout.getStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(payout.getRequestedAmount()).isEqualTo(15_000);
        assertThat(payout.getProviderSellerId()).isNull();
        assertThat(finalizedProfile.getProviderSellerId()).isEqualTo("seller-synthetic-claim-first");
        assertThat(finalizedProfile.getKycStatus()).isNull();
        assertThat(finalizedProfile.getMetadataJson()).isNull();
    }

    @Test
    void payoutClaimWaitsForFinalizerAndUsesTheRetainedFallback() throws Exception {
        LocalDateTime dueAt = LocalDateTime.now().minusMinutes(1);
        UserEntity seller = savePendingUser(dueAt);
        sellerPayoutProfileRepository.saveAndFlush(SellerPayoutProfile.builder()
                .userId(seller.getId())
                .provider("TOSS")
                .providerSellerId("seller-synthetic-finalizer-first")
                .kycStatus("VERIFIED")
                .metadataJson("{\"source\":\"synthetic-finalizer-first\"}")
                .build());
        PaymentTransaction payment = saveApprovedPaymentForSeller(seller);

        CountDownLatch finalizerHoldingSellerLock = new CountDownLatch(1);
        CountDownLatch releaseFinalizer = new CountDownLatch(1);
        doAnswer(invocation -> {
            finalizerHoldingSellerLock.countDown();
            if (!releaseFinalizer.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release synthetic finalizer");
            }
            return null;
        }).when(partyService).handleUserDeletion(seller.getId());

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> finalization = workers.submit(
                    () -> accountDeletionFinalizationService.finalizeIfDue(seller.getId(), LocalDateTime.now()));
            assertThat(finalizerHoldingSellerLock.await(5, TimeUnit.SECONDS)).isTrue();

            CountDownLatch claimStarted = new CountDownLatch(1);
            Future<PayoutClaimService.ClaimedPayout> claim = workers.submit(() -> {
                claimStarted.countDown();
                return payoutClaimService.claimInitial(payment.getId(), true);
            });
            assertThat(claimStarted.await(5, TimeUnit.SECONDS)).isTrue();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> claim.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseFinalizer.countDown();
            assertThat(finalization.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(claim.get(5, TimeUnit.SECONDS).action())
                    .isEqualTo(PayoutClaimService.ClaimAction.CALL_PROVIDER);
        } finally {
            releaseFinalizer.countDown();
            workers.shutdownNow();
        }

        PayoutTransaction payout = payoutTransactionRepository
                .findTopByPaymentTransactionIdOrderByIdDesc(payment.getId())
                .orElseThrow();
        SellerPayoutProfile finalizedProfile = sellerPayoutProfileRepository
                .findByUserIdAndProvider(seller.getId(), "TOSS")
                .orElseThrow();
        assertThat(payout.getStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(payout.getRequestedAmount()).isEqualTo(15_000);
        assertThat(finalizedProfile.getProviderSellerId()).isEqualTo("seller-synthetic-finalizer-first");
        assertThat(finalizedProfile.getKycStatus()).isNull();
        assertThat(finalizedProfile.getMetadataJson()).isNull();
    }

    @Test
    void concurrentReportCommitIsVisibleBeforeFinalizerRedactsAuthoredPost() throws Exception {
        LocalDateTime dueAt = LocalDateTime.now().minusMinutes(1);
        UserEntity author = savePendingUser(dueAt);
        UserEntity reporter = userRepository.saveAndFlush(UserEntity.builder()
                .uniqueId(UUID.randomUUID())
                .handle("r" + UUID.randomUUID().toString().substring(0, 8))
                .name("Synthetic reporter")
                .email("reporter-" + UUID.randomUUID() + "@example.test")
                .role("ROLE_USER")
                .enabled(true)
                .pendingDeletion(false)
                .build());
        TeamEntity team = teamRepository.saveAndFlush(TeamEntity.builder()
                .teamId("SYNTHETIC-" + UUID.randomUUID().toString().substring(0, 8))
                .teamName("Synthetic team")
                .teamShortName("SYN")
                .city("Synthetic city")
                .build());
        CheerPost post = cheerPostRepository.saveAndFlush(CheerPost.builder()
                .team(team)
                .author(author)
                .content("synthetic authored evidence")
                .build());

        CountDownLatch reportHoldingPostLock = new CountDownLatch(1);
        CountDownLatch releaseReport = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        TransactionTemplate reportTransaction = new TransactionTemplate(transactionManager);
        try {
            Future<?> reportWrite = workers.submit(() -> reportTransaction.execute(status -> {
                CheerPost lockedPost = cheerPostRepository.findByIdForWrite(post.getId()).orElseThrow();
                reportHoldingPostLock.countDown();
                try {
                    if (!releaseReport.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release synthetic report lock");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Synthetic report writer interrupted", interrupted);
                }
                cheerReportRepository.saveAndFlush(CheerPostReport.builder()
                        .post(lockedPost)
                        .reporter(reporter)
                        .reason(ReportReason.OTHER)
                        .description("synthetic moderation evidence")
                        .build());
                return null;
            }));
            assertThat(reportHoldingPostLock.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Boolean> finalization = workers.submit(
                    () -> accountDeletionFinalizationService.finalizeIfDue(author.getId(), LocalDateTime.now()));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> finalization.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseReport.countDown();
            reportWrite.get(5, TimeUnit.SECONDS);
            assertThat(finalization.get(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseReport.countDown();
            workers.shutdownNow();
        }

        CheerPost postAfterFinalization = cheerPostRepository.findById(post.getId()).orElseThrow();
        assertThat(postAfterFinalization.getContent()).isEqualTo("synthetic authored evidence");
        assertThat(cheerReportRepository.findByPostId(post.getId()))
                .singleElement()
                .extracting(CheerPostReport::getDescription)
                .isEqualTo("synthetic moderation evidence");
    }

    @Test
    void moderationProtectionIncludesOpenReportsAndAppealsButNotClosedCases() {
        LocalDateTime dueAt = LocalDateTime.now().minusMinutes(1);
        UserEntity author = savePendingUser(dueAt);
        UserEntity reporter = userRepository.saveAndFlush(UserEntity.builder()
                .uniqueId(UUID.randomUUID())
                .handle("r" + UUID.randomUUID().toString().substring(0, 8))
                .name("Synthetic reporter")
                .email("reporter-" + UUID.randomUUID() + "@example.test")
                .role("ROLE_USER")
                .enabled(true)
                .pendingDeletion(false)
                .build());
        TeamEntity team = teamRepository.saveAndFlush(TeamEntity.builder()
                .teamId("SYNTHETIC-" + UUID.randomUUID().toString().substring(0, 8))
                .teamName("Synthetic team")
                .teamShortName("SYN")
                .city("Synthetic city")
                .build());
        CheerPost openReportPost = cheerPostRepository.saveAndFlush(CheerPost.builder()
                .team(team).author(author).content("open report source").build());
        CheerPost openAppealPost = cheerPostRepository.saveAndFlush(CheerPost.builder()
                .team(team).author(author).content("open appeal source").build());
        CheerPost closedPost = cheerPostRepository.saveAndFlush(CheerPost.builder()
                .team(team).author(author).content("closed case source").build());
        cheerReportRepository.saveAndFlush(CheerPostReport.builder()
                .post(openReportPost).reporter(reporter).reason(ReportReason.OTHER)
                .status(CheerPostReport.ReportStatus.IN_REVIEW)
                .appealStatus(CheerPostReport.AppealStatus.NONE)
                .description("synthetic open report evidence").build());
        cheerReportRepository.saveAndFlush(CheerPostReport.builder()
                .post(openAppealPost).reporter(reporter).reason(ReportReason.OTHER)
                .status(CheerPostReport.ReportStatus.CLOSED)
                .appealStatus(CheerPostReport.AppealStatus.REQUESTED)
                .description("synthetic open appeal evidence").build());
        cheerReportRepository.saveAndFlush(CheerPostReport.builder()
                .post(closedPost).reporter(reporter).reason(ReportReason.OTHER)
                .status(CheerPostReport.ReportStatus.CLOSED)
                .appealStatus(CheerPostReport.AppealStatus.REJECTED)
                .description("synthetic closed evidence").build());

        assertThat(cheerReportRepository.findOpenModerationPostIdsByAuthorId(author.getId()))
                .containsExactlyInAnyOrder(openReportPost.getId(), openAppealPost.getId())
                .doesNotContain(closedPost.getId());
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

    private PaymentTransaction saveApprovedPaymentForSeller(UserEntity seller) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        UserEntity buyer = userRepository.saveAndFlush(UserEntity.builder()
                .uniqueId(UUID.randomUUID())
                .handle("b" + suffix.substring(0, 8))
                .name("Synthetic buyer")
                .email("buyer-" + suffix + "@example.test")
                .role("ROLE_USER")
                .enabled(true)
                .pendingDeletion(false)
                .build());
        String orderId = "synthetic-order-" + suffix;
        String paymentKey = "synthetic-payment-" + suffix;
        Party party = partyRepository.saveAndFlush(Party.builder()
                .hostId(seller.getId())
                .hostName("Synthetic seller")
                .hostBadge(Party.BadgeType.NEW)
                .teamId("SYNTHETIC")
                .gameDate(java.time.LocalDate.now().plusDays(7))
                .gameTime(java.time.LocalTime.NOON)
                .stadium("Synthetic stadium")
                .homeTeam("SYNTHETIC-HOME")
                .awayTeam("SYNTHETIC-AWAY")
                .section("Synthetic section")
                .maxParticipants(4)
                .currentParticipants(2)
                .description("Synthetic payout claim party")
                .status(Party.PartyStatus.SELLING)
                .price(15_000)
                .build());
        PartyApplication application = partyApplicationRepository.saveAndFlush(PartyApplication.builder()
                .partyId(party.getId())
                .applicantId(buyer.getId())
                .applicantName("Synthetic buyer")
                .applicantBadge(Party.BadgeType.NEW)
                .message("Synthetic payout claim fixture")
                .depositAmount(15_000)
                .isPaid(true)
                .isApproved(true)
                .isRejected(false)
                .paymentType(PartyApplication.PaymentType.FULL)
                .paymentKey(paymentKey)
                .orderId(orderId)
                .build());
        return paymentTransactionRepository.saveAndFlush(PaymentTransaction.builder()
                .partyId(application.getPartyId())
                .applicationId(application.getId())
                .buyerUserId(buyer.getId())
                .sellerUserId(seller.getId())
                .flowType(PaymentFlowType.SELLING_FULL)
                .orderId(orderId)
                .paymentKey(paymentKey)
                .grossAmount(15_000)
                .feeAmount(0)
                .refundAmount(0)
                .netAmount(15_000)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.PENDING)
                .build());
    }
}
