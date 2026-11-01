package com.example.mate.integration;

import com.example.auth.service.AccountDeletionFinancialService;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentFlowType;
import com.example.mate.entity.PaymentStatus;
import com.example.mate.entity.PaymentTransaction;
import com.example.mate.entity.PayoutTransaction;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PaymentTransactionRepository;
import com.example.mate.repository.PayoutTransactionRepository;
import com.example.mate.service.PartyLifecycleMutationService;
import com.example.mate.service.PaymentMetricsService;
import com.example.mate.service.PayoutClaimService;
import com.example.mate.service.PayoutStateService;
import com.example.mate.service.SellerRecoveryService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:payout_dispatch_expiry;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PayoutDispatchIntentExpiryIntegrationTest {

    @MockitoSpyBean
    private PartyRepository partyRepository;

    @Autowired
    private PartyApplicationRepository applicationRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private PayoutTransactionRepository payoutTransactionRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void dispatchIntentLockWinsBeforeExpiry_andExpiryRetainsManualHold() throws Exception {
        Seed seed = seedEligiblePayout();
        PayoutClaimService claimService = newClaimService();
        PartyLifecycleMutationService lifecycleService = newLifecycleService();
        CountDownLatch intentPrepared = new CountDownLatch(1);
        CountDownLatch releaseIntentTransaction = new CountDownLatch(1);
        AtomicReference<Thread> expiryThread = new AtomicReference<>();
        CountDownLatch expiryPartyLockAttempted = new CountDownLatch(1);
        CountDownLatch expiryPartyLockReturned = new CountDownLatch(1);
        watchPartyLock(seed.partyId(), expiryThread, expiryPartyLockAttempted, expiryPartyLockReturned);

        Future<PayoutClaimService.DispatchIntentClaim> dispatch = executor.submit(() -> inTransaction(() -> {
            PayoutClaimService.DispatchIntentClaim claim = claimService.claimProviderDispatchIntent(
                    seed.paymentId(), seed.payoutId());
            intentPrepared.countDown();
            await(releaseIntentTransaction);
            return claim;
        }));

        assertThat(intentPrepared.await(5, TimeUnit.SECONDS)).isTrue();
        Future<?> expiry = executor.submit(() -> inTransaction(() -> {
            expiryThread.set(Thread.currentThread());
            lifecycleService.expirePendingParty(seed.partyId(), LocalDate.now().plusDays(1));
            return null;
        }));
        assertThat(expiryPartyLockAttempted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(expiryPartyLockReturned.await(250, TimeUnit.MILLISECONDS)).isFalse();

        releaseIntentTransaction.countDown();
        PayoutClaimService.DispatchIntentClaim claim = dispatch.get(5, TimeUnit.SECONDS);
        expiry.get(5, TimeUnit.SECONDS);
        assertThat(expiryPartyLockReturned.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(claim.dispatchAllowed()).isTrue();
        assertThat(claim.payout().getProviderDispatchIntentAt()).isNotNull();
        assertManualExpiryHold(seed);

        PayoutClaimService.DispatchIntentClaim replay = inTransaction(
                () -> claimService.claimProviderDispatchIntent(seed.paymentId(), seed.payoutId()));
        assertThat(replay.dispatchAllowed()).isFalse();
        assertThat(replay.payout().getProviderDispatchIntentAt()).isNotNull();
        assertManualExpiryHold(seed);

        PayoutClaimService.ClaimedPayout retry = inTransaction(
                () -> claimService.claimRetry(seed.payoutId(), true));
        assertThat(retry.action()).isEqualTo(PayoutClaimService.ClaimAction.NONE);
        assertManualExpiryHold(seed);
    }

    @Test
    void expiryLockWinsBeforeDispatchIntent_andDispatchIsDenied() throws Exception {
        Seed seed = seedEligiblePayout();
        PayoutClaimService claimService = newClaimService();
        PartyLifecycleMutationService lifecycleService = newLifecycleService();
        CountDownLatch expiryApplied = new CountDownLatch(1);
        CountDownLatch releaseExpiryTransaction = new CountDownLatch(1);
        AtomicReference<Thread> dispatchThread = new AtomicReference<>();
        CountDownLatch dispatchPartyLockAttempted = new CountDownLatch(1);
        CountDownLatch dispatchPartyLockReturned = new CountDownLatch(1);

        Future<?> expiry = executor.submit(() -> inTransaction(() -> {
            lifecycleService.expirePendingParty(seed.partyId(), LocalDate.now().plusDays(1));
            expiryApplied.countDown();
            await(releaseExpiryTransaction);
            return null;
        }));

        assertThat(expiryApplied.await(5, TimeUnit.SECONDS)).isTrue();
        watchPartyLock(seed.partyId(), dispatchThread, dispatchPartyLockAttempted, dispatchPartyLockReturned);
        Future<PayoutClaimService.DispatchIntentClaim> dispatch = executor.submit(() -> inTransaction(() -> {
            dispatchThread.set(Thread.currentThread());
            return claimService.claimProviderDispatchIntent(seed.paymentId(), seed.payoutId());
        }));
        assertThat(dispatchPartyLockAttempted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(dispatchPartyLockReturned.await(250, TimeUnit.MILLISECONDS)).isFalse();

        releaseExpiryTransaction.countDown();
        expiry.get(5, TimeUnit.SECONDS);
        PayoutClaimService.DispatchIntentClaim claim = dispatch.get(5, TimeUnit.SECONDS);
        assertThat(dispatchPartyLockReturned.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(claim.dispatchAllowed()).isFalse();
        assertThat(claim.payout().getProviderDispatchIntentAt()).isNull();
        assertManualExpiryHold(seed);
    }

    @Test
    void applicantDeletionLockWinsBeforeRetryDispatch_andPaidApplicationIsHeldForManualReconciliation()
            throws Exception {
        Seed seed = seedEligiblePayout();
        PayoutClaimService claimService = newClaimService();
        com.example.mate.service.PartyService deletionService = newPartyService();
        CountDownLatch deletionApplied = new CountDownLatch(1);
        CountDownLatch releaseDeletionTransaction = new CountDownLatch(1);
        AtomicReference<Thread> dispatchThread = new AtomicReference<>();
        CountDownLatch dispatchPartyLockAttempted = new CountDownLatch(1);
        CountDownLatch dispatchPartyLockReturned = new CountDownLatch(1);
        watchPartyLock(seed.partyId(), dispatchThread, dispatchPartyLockAttempted, dispatchPartyLockReturned);

        Future<?> deletion = executor.submit(() -> inTransaction(() -> {
            deletionService.handleUserDeletion(seed.applicantId());
            deletionApplied.countDown();
            await(releaseDeletionTransaction);
            return null;
        }));

        assertThat(deletionApplied.await(5, TimeUnit.SECONDS)).isTrue();
        Future<PayoutClaimService.DispatchIntentClaim> dispatch = executor.submit(() -> inTransaction(() -> {
            dispatchThread.set(Thread.currentThread());
            PayoutClaimService.ClaimedPayout retry = claimService.claimRetry(seed.payoutId(), true);
            assertThat(retry.action()).isEqualTo(PayoutClaimService.ClaimAction.CALL_PROVIDER);
            return claimService.claimProviderDispatchIntent(seed.paymentId(), seed.payoutId());
        }));
        assertThat(dispatchPartyLockAttempted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(dispatchPartyLockReturned.await(250, TimeUnit.MILLISECONDS)).isFalse();

        releaseDeletionTransaction.countDown();
        deletion.get(5, TimeUnit.SECONDS);
        PayoutClaimService.DispatchIntentClaim claim = dispatch.get(5, TimeUnit.SECONDS);
        assertThat(dispatchPartyLockReturned.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(claim.dispatchAllowed()).isFalse();
        assertThat(claim.payout().getProviderDispatchIntentAt()).isNull();
        assertApplicantDeletionState(seed);
        assertManualApplicantDeletionHold(seed);
    }

    @Test
    void retryDispatchIntentLockWinsBeforeApplicantDeletion_andIntentRemainsDurable() throws Exception {
        Seed seed = seedEligiblePayout();
        PayoutClaimService claimService = newClaimService();
        com.example.mate.service.PartyService deletionService = newPartyService();
        CountDownLatch intentPrepared = new CountDownLatch(1);
        CountDownLatch releaseIntentTransaction = new CountDownLatch(1);
        AtomicReference<Thread> deletionThread = new AtomicReference<>();
        CountDownLatch deletionPartyLockAttempted = new CountDownLatch(1);
        CountDownLatch deletionPartyLockReturned = new CountDownLatch(1);
        watchPartyLock(seed.partyId(), deletionThread, deletionPartyLockAttempted, deletionPartyLockReturned);

        Future<PayoutClaimService.DispatchIntentClaim> dispatch = executor.submit(() -> inTransaction(() -> {
            PayoutClaimService.DispatchIntentClaim claim = claimService.claimProviderDispatchIntent(
                    seed.paymentId(), seed.payoutId());
            intentPrepared.countDown();
            await(releaseIntentTransaction);
            return claim;
        }));

        assertThat(intentPrepared.await(5, TimeUnit.SECONDS)).isTrue();
        Future<?> deletion = executor.submit(() -> inTransaction(() -> {
            deletionThread.set(Thread.currentThread());
            deletionService.handleUserDeletion(seed.applicantId());
            return null;
        }));
        assertThat(deletionPartyLockAttempted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(deletionPartyLockReturned.await(250, TimeUnit.MILLISECONDS)).isFalse();

        releaseIntentTransaction.countDown();
        PayoutClaimService.DispatchIntentClaim claim = dispatch.get(5, TimeUnit.SECONDS);
        deletion.get(5, TimeUnit.SECONDS);
        assertThat(deletionPartyLockReturned.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(claim.dispatchAllowed()).isTrue();
        assertThat(claim.payout().getProviderDispatchIntentAt()).isNotNull();
        assertApplicantDeletionState(seed);
        assertThat(payoutTransactionRepository.findById(seed.payoutId()).orElseThrow().getProviderDispatchIntentAt())
                .isNotNull();
        assertThat(paymentTransactionRepository.findById(seed.paymentId()).orElseThrow().getPaymentStatus())
                .isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void applicantDeletionDoesNotDisableExistingProviderReferenceStatusPolling() {
        Seed seed = seedEligiblePayout();
        com.example.mate.service.PartyService deletionService = newPartyService();
        inTransaction(() -> {
            deletionService.handleUserDeletion(seed.applicantId());
            PayoutTransaction payout = payoutTransactionRepository.findByIdForUpdate(seed.payoutId()).orElseThrow();
            payout.setProviderRef("synthetic-existing-provider-ref");
            payoutTransactionRepository.saveAndFlush(payout);
            return null;
        });

        PayoutClaimService.ClaimedPayout retry = inTransaction(
                () -> newClaimService().claimRetry(seed.payoutId(), true));

        assertThat(retry.action()).isEqualTo(PayoutClaimService.ClaimAction.POLL_PROVIDER);
        assertThat(retry.payout().getProviderRef()).isEqualTo("synthetic-existing-provider-ref");
        assertThat(retry.payout().getProviderDispatchIntentAt()).isNull();
        assertApplicantDeletionState(seed);
    }

    private Seed seedEligiblePayout() {
        return inTransaction(() -> {
            Party party = partyRepository.saveAndFlush(newParty());
            long applicantId = 600L;
            String orderId = "DISPATCH-RACE-" + System.nanoTime();
            PartyApplication application = applicationRepository.saveAndFlush(PartyApplication.builder()
                    .partyId(party.getId())
                    .applicantId(applicantId)
                    .applicantName("Synthetic applicant")
                    .applicantBadge(Party.BadgeType.NEW)
                    .message("Synthetic approved application")
                    .depositAmount(12000)
                    .isPaid(true)
                    .isApproved(true)
                    .isRejected(false)
                    .paymentType(PartyApplication.PaymentType.DEPOSIT)
                    .paymentKey("PAYMENT-" + orderId)
                    .orderId(orderId)
                    .build());
            PaymentTransaction payment = paymentTransactionRepository.saveAndFlush(PaymentTransaction.builder()
                    .partyId(party.getId())
                    .applicationId(application.getId())
                    .buyerUserId(applicantId)
                    .sellerUserId(party.getHostId())
                    .flowType(PaymentFlowType.DEPOSIT)
                    .orderId(orderId)
                    .paymentKey("PAYMENT-" + orderId)
                    .grossAmount(12000)
                    .feeAmount(0)
                    .refundAmount(0)
                    .netAmount(12000)
                    .paymentStatus(PaymentStatus.PAID)
                    .settlementStatus(SettlementStatus.REQUESTED)
                    .build());
            PayoutTransaction payout = payoutTransactionRepository.saveAndFlush(PayoutTransaction.builder()
                    .paymentTransactionId(payment.getId())
                    .sellerId(party.getHostId())
                    .requestedAmount(12000)
                    .status(SettlementStatus.REQUESTED)
                    .claimProtocol("SNAPSHOT_V1")
                    .build());
            return new Seed(party.getId(), application.getId(), payment.getId(), payout.getId(), applicantId);
        });
    }

    private void assertManualExpiryHold(Seed seed) {
        Party party = partyRepository.findById(seed.partyId()).orElseThrow();
        PartyApplication application = applicationRepository.findById(seed.applicationId()).orElseThrow();
        PaymentTransaction payment = paymentTransactionRepository.findById(seed.paymentId()).orElseThrow();
        PayoutTransaction payout = payoutTransactionRepository.findById(seed.payoutId()).orElseThrow();

        assertThat(party.getStatus()).isEqualTo(Party.PartyStatus.FAILED);
        assertThat(application.getIsApproved()).isTrue();
        assertThat(application.getIsPaid()).isTrue();
        assertThat(application.getIsRejected()).isFalse();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getSettlementStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(payout.getStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(payout.getFailureCode()).isEqualTo("PAYOUT_MANUAL_RECONCILIATION_REQUIRED");
        assertThat(payout.getNextRetryAt()).isNull();
    }

    private void assertApplicantDeletionState(Seed seed) {
        Party party = partyRepository.findById(seed.partyId()).orElseThrow();
        PartyApplication application = applicationRepository.findById(seed.applicationId()).orElseThrow();
        assertThat(party.getCurrentParticipants()).isEqualTo(1);
        assertThat(application.getIsApproved()).isFalse();
        assertThat(application.getIsRejected()).isTrue();
        assertThat(application.getIsPaid()).isTrue();
    }

    private void assertManualApplicantDeletionHold(Seed seed) {
        PaymentTransaction payment = paymentTransactionRepository.findById(seed.paymentId()).orElseThrow();
        PayoutTransaction payout = payoutTransactionRepository.findById(seed.payoutId()).orElseThrow();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getSettlementStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(payout.getStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(payout.getFailureCode()).isEqualTo("PAYOUT_MANUAL_RECONCILIATION_REQUIRED");
        assertThat(payout.getNextRetryAt()).isNull();
        assertThat(payout.getProviderDispatchIntentAt()).isNull();
    }

    private Party newParty() {
        return Party.builder()
                .hostId(500L)
                .hostName("Synthetic host")
                .hostBadge(Party.BadgeType.NEW)
                .teamId("LG")
                .gameDate(LocalDate.now().minusDays(1))
                .gameTime(LocalTime.of(18, 30))
                .stadium("Synthetic stadium")
                .homeTeam("LG")
                .awayTeam("KT")
                .section("Synthetic section")
                .maxParticipants(4)
                .currentParticipants(2)
                .description("Synthetic party for payout concurrency testing")
                .ticketVerified(true)
                .status(Party.PartyStatus.PENDING)
                .build();
    }

    private PayoutClaimService newClaimService() {
        return new PayoutClaimService(
                payoutTransactionRepository,
                paymentTransactionRepository,
                applicationRepository,
                partyRepository,
                mock(SellerRecoveryService.class),
                mock(PaymentMetricsService.class),
                mock(AccountDeletionFinancialService.class));
    }

    private void watchPartyLock(
            Long partyId,
            AtomicReference<Thread> contenderThread,
            CountDownLatch lockAttempted,
            CountDownLatch lockReturned) {
        doAnswer(invocation -> {
            boolean contender = Thread.currentThread() == contenderThread.get()
                    && partyId.equals(invocation.getArgument(0));
            if (contender) {
                lockAttempted.countDown();
            }
            Object result = java.util.Optional.ofNullable(entityManager.find(
                    Party.class,
                    partyId,
                    LockModeType.PESSIMISTIC_WRITE,
                    java.util.Map.of("jakarta.persistence.lock.timeout", 5000)));
            if (contender) {
                lockReturned.countDown();
            }
            return result;
        }).when(partyRepository).findByIdForUpdate(anyLong());
    }

    private PartyLifecycleMutationService newLifecycleService() {
        return new PartyLifecycleMutationService(
                partyRepository,
                applicationRepository,
                paymentTransactionRepository,
                payoutTransactionRepository);
    }

    private com.example.mate.service.PartyService newPartyService() {
        return new com.example.mate.service.PartyService(
                partyRepository,
                mock(com.example.auth.repository.UserRepository.class),
                applicationRepository,
                mock(com.example.mate.service.PartyMapper.class),
                mock(com.example.mate.service.PartyFavoriteService.class),
                mock(com.example.auth.repository.UserProviderRepository.class),
                mock(com.example.auth.service.PublicVisibilityVerifier.class),
                mock(com.example.kbo.service.TicketVerificationTokenStore.class),
                mock(com.example.notification.service.NotificationService.class),
                mock(com.example.mate.service.MateHistoryMetricsService.class));
    }

    private <T> T inTransaction(java.util.concurrent.Callable<T> callback) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> {
            try {
                return callback.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out while waiting for payout dispatch race release");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private record Seed(Long partyId, Long applicationId, Long paymentId, Long payoutId, Long applicantId) {
    }
}
