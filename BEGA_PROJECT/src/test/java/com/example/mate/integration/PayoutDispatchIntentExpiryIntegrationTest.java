package com.example.mate.integration;

import com.example.admin.dto.MateLinkReconciliationCasePageDto;
import com.example.admin.service.MateLinkReconciliationCaseAdminService;
import com.example.auth.entity.UserEntity;
import com.example.auth.service.AccountDeletionFinancialService;
import com.example.auth.repository.UserRepository;
import com.example.auth.repository.UserProviderRepository;
import com.example.auth.service.PublicVisibilityVerifier;
import com.example.common.jobs.JobSubmissionGateway;
import com.example.kbo.service.TicketVerificationTokenStore;
import com.example.mate.service.MateHistoryMetricsService;
import com.example.mate.service.PartyFavoriteService;
import com.example.mate.service.PartyMapper;
import com.example.mate.service.PartyService;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentFlowType;
import com.example.mate.entity.MateLinkReconciliationCase;
import com.example.mate.entity.MateLinkReconciliationReasonCode;
import com.example.mate.entity.PaymentStatus;
import com.example.mate.entity.PaymentTransaction;
import com.example.mate.entity.PayoutTransaction;
import com.example.mate.entity.SellerPayoutProfile;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.exception.InvalidApplicationStatusException;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PaymentTransactionRepository;
import com.example.mate.repository.PayoutTransactionRepository;
import com.example.mate.repository.SellerPayoutProfileRepository;
import com.example.mate.repository.MateLinkReconciliationCaseRepository;
import com.example.mate.service.MateLinkReconciliationCaseRecorder;
import com.example.mate.service.MateLinkReconciliationCaseWriter;
import com.example.mate.service.PartyLifecycleMutationService;
import com.example.mate.service.PaymentMetricsService;
import com.example.mate.service.PayoutClaimService;
import com.example.mate.service.PayoutService;
import com.example.mate.service.PayoutStateService;
import com.example.mate.service.SellerPayoutProfileService;
import com.example.mate.service.SellerRecoveryService;
import com.example.mate.service.payout.PayoutGateway;
import com.example.notification.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({
        PayoutClaimService.class,
        PayoutStateService.class,
        PartyLifecycleMutationService.class,
        PartyService.class,
        MateLinkReconciliationCaseWriter.class,
        MateLinkReconciliationCaseRecorder.class,
        MateLinkReconciliationCaseAdminService.class,
        AccountDeletionFinancialService.class
})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:payout_dispatch_intent_expiry;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "payment.payout.enabled=true",
        "payment.payout.provider=SIM"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PayoutDispatchIntentExpiryIntegrationTest {

    @Autowired
    private PayoutClaimService payoutClaimService;

    @Autowired
    private PayoutStateService payoutStateService;

    @Autowired
    private PartyLifecycleMutationService partyLifecycleMutationService;

    @Autowired
    private PartyService partyService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private PartyApplicationRepository partyApplicationRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private PayoutTransactionRepository payoutTransactionRepository;

    @Autowired
    private SellerPayoutProfileRepository sellerPayoutProfileRepository;

    @Autowired
    private AccountDeletionFinancialService accountDeletionFinancialService;

    @Autowired
    private MateLinkReconciliationCaseRepository reconciliationCaseRepository;

    @Autowired
    private MateLinkReconciliationCaseRecorder reconciliationCaseRecorder;

    @Autowired
    private MateLinkReconciliationCaseAdminService reconciliationCaseAdminService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private SellerRecoveryService sellerRecoveryService;

    @MockitoBean
    private PaymentMetricsService paymentMetricsService;

    @MockitoBean
    private PartyMapper partyMapper;

    @MockitoBean
    private PartyFavoriteService partyFavoriteService;

    @MockitoBean
    private UserProviderRepository userProviderRepository;

    @MockitoBean
    private PublicVisibilityVerifier publicVisibilityVerifier;

    @MockitoBean
    private TicketVerificationTokenStore ticketVerificationTokenStore;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private MateHistoryMetricsService mateHistoryMetricsService;

    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void expiryLockWinningPreventsDispatchIntentAndHoldsPayout() throws Exception {
        Scenario scenario = createScenario("expiry-wins");
        CountDownLatch applicationLocked = new CountDownLatch(1);
        CountDownLatch releaseApplicationLock = new CountDownLatch(1);
        int holderSessionId = holdApplicationLock(scenario, applicationLocked, releaseApplicationLock);

        Future<?> expiry = executor.submit(() ->
                partyLifecycleMutationService.expirePendingParty(scenario.partyId(), LocalDate.now()));
        int expirySessionId = awaitBlockedSession(holderSessionId);

        Future<Boolean> dispatch = executor.submit(() ->
                payoutClaimService.claimProviderDispatchIntent(scenario.paymentId(), scenario.payoutId()));
        awaitBlockedSession(expirySessionId);

        releaseApplicationLock.countDown();
        expiry.get(5, TimeUnit.SECONDS);
        assertThat(dispatch.get(5, TimeUnit.SECONDS)).isFalse();

        PayoutTransaction savedPayout = payoutTransactionRepository.findById(scenario.payoutId()).orElseThrow();
        assertThat(savedPayout.getProviderDispatchIntentAt()).isNull();
        assertThat(savedPayout.getFailureCode())
                .isEqualTo("PAYOUT_MANUAL_RECONCILIATION_REQUIRED");
        assertThat(savedPayout.getNextRetryAt()).isNull();
    }

    @Test
    void dispatchIntentCommitWinningAllowsOneInFlightRequestButExpiryRetainsManualHold() throws Exception {
        Scenario scenario = createScenario("dispatch-wins");
        CountDownLatch applicationLocked = new CountDownLatch(1);
        CountDownLatch releaseApplicationLock = new CountDownLatch(1);
        int holderSessionId = holdApplicationLock(scenario, applicationLocked, releaseApplicationLock);

        Future<Boolean> dispatch = executor.submit(() ->
                payoutClaimService.claimProviderDispatchIntent(scenario.paymentId(), scenario.payoutId()));
        int dispatchSessionId = awaitBlockedSession(holderSessionId);

        Future<?> expiry = executor.submit(() ->
                partyLifecycleMutationService.expirePendingParty(scenario.partyId(), LocalDate.now()));
        awaitBlockedSession(dispatchSessionId);

        releaseApplicationLock.countDown();
        assertThat(dispatch.get(5, TimeUnit.SECONDS)).isTrue();
        expiry.get(5, TimeUnit.SECONDS);

        PayoutTransaction savedPayout = payoutTransactionRepository.findById(scenario.payoutId()).orElseThrow();
        assertThat(savedPayout.getProviderDispatchIntentAt()).isNotNull();
        assertThat(savedPayout.getFailureCode())
                .isEqualTo("PAYOUT_MANUAL_RECONCILIATION_REQUIRED");
        assertThat(savedPayout.getNextRetryAt()).isNull();
        assertThat(payoutClaimService.claimProviderDispatchIntent(scenario.paymentId(), scenario.payoutId())).isFalse();
    }

    @Test
    void initialPayoutClaimWaitsForApprovalCommitBeforeReservingOffset() throws Exception {
        Scenario scenario = createScenario("initial-claim-after-approval");
        prepareInitialPayoutClaimScenario(scenario, false);
        CountDownLatch applicationLocked = new CountDownLatch(1);
        CountDownLatch releaseApplicationLock = new CountDownLatch(1);
        int holderSessionId = holdPartyAndApplicationLock(
                scenario, true, applicationLocked, releaseApplicationLock);

        Future<PayoutClaimService.ClaimedPayout> initialClaim = executor.submit(() ->
                payoutClaimService.claimInitial(scenario.paymentId(), true));
        try {
            awaitBlockedSession(holderSessionId);
            releaseApplicationLock.countDown();

            PayoutClaimService.ClaimedPayout claimed = initialClaim.get(5, TimeUnit.SECONDS);
            assertThat(claimed.action()).isEqualTo(PayoutClaimService.ClaimAction.CALL_PROVIDER);
            assertThat(partyApplicationRepository.findById(scenario.applicationId()).orElseThrow().getIsApproved())
                    .isTrue();
            assertThat(payoutTransactionRepository.findById(scenario.payoutId()).orElseThrow().getStatus())
                    .isEqualTo(SettlementStatus.REQUESTED);
            verify(sellerRecoveryService).reserveOffset(any(PayoutTransaction.class), any(), anyInt());
        } finally {
            releaseApplicationLock.countDown();
        }
    }

    @Test
    void initialPayoutClaimCommittedBeforeApprovalRejectsWithoutOffsetReservation() throws Exception {
        Scenario scenario = createScenario("initial-claim-before-approval");
        prepareInitialPayoutClaimScenario(scenario, false);
        CountDownLatch applicationLocked = new CountDownLatch(1);
        CountDownLatch releaseApplicationLock = new CountDownLatch(1);
        int holderSessionId = holdPartyAndApplicationLock(
                scenario, false, applicationLocked, releaseApplicationLock);

        Future<PayoutClaimService.ClaimedPayout> initialClaim = executor.submit(() ->
                payoutClaimService.claimInitial(scenario.paymentId(), true));
        try {
            awaitBlockedSession(holderSessionId);
            releaseApplicationLock.countDown();

            assertThatThrownBy(() -> initialClaim.get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(InvalidApplicationStatusException.class);
            inTransaction(() -> {
                partyRepository.findByIdForUpdate(scenario.partyId()).orElseThrow();
                PartyApplication application = partyApplicationRepository
                        .findByOrderIdForUpdate(scenario.orderId()).orElseThrow();
                application.setIsApproved(true);
                application.setApprovedAt(java.time.Instant.now());
                partyApplicationRepository.saveAndFlush(application);
                return null;
            });
            assertThat(partyApplicationRepository.findById(scenario.applicationId()).orElseThrow()
                    .getIsApproved()).isTrue();
            assertThat(payoutTransactionRepository.findById(scenario.payoutId()).orElseThrow().getStatus())
                    .isEqualTo(SettlementStatus.PENDING);
            assertThat(payoutTransactionRepository.findById(scenario.payoutId()).orElseThrow()
                    .getRecoveryOffsetReservedAt()).isNull();
            verify(sellerRecoveryService, never()).reserveOffset(any(PayoutTransaction.class), any(), anyInt());
        } finally {
            releaseApplicationLock.countDown();
        }
    }

    @Test
    void inFlightProviderCompletionAfterExpiryRetainsManualHoldAndCannotRedispatch() throws Exception {
        Scenario scenario = createScenario("provider-response-after-expiry");
        PayoutGateway gateway = syntheticGateway();
        CountDownLatch providerRequestStarted = new CountDownLatch(1);
        CountDownLatch providerResponseReleased = new CountDownLatch(1);
        when(gateway.requestPayout(any(PayoutGateway.PayoutRequest.class))).thenAnswer(invocation -> {
            providerRequestStarted.countDown();
            await(providerResponseReleased);
            return new PayoutGateway.PayoutResult("synthetic-completed-ref", "COMPLETED");
        });
        PayoutService payoutService = newPayoutService(gateway);

        Future<?> payoutRequest = executor.submit(() -> payoutService.retryPayout(scenario.payoutId()));
        try {
            assertThat(providerRequestStarted.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> expiry = executor.submit(() ->
                    partyLifecycleMutationService.expirePendingParty(scenario.partyId(), LocalDate.now()));
            expiry.get(5, TimeUnit.SECONDS);

            PayoutTransaction heldBeforeProviderResponse = payoutTransactionRepository
                    .findById(scenario.payoutId()).orElseThrow();
            assertThat(heldBeforeProviderResponse.getFailureCode())
                    .isEqualTo("PAYOUT_MANUAL_RECONCILIATION_REQUIRED");
            assertThat(heldBeforeProviderResponse.getNextRetryAt()).isNull();
        } finally {
            providerResponseReleased.countDown();
        }

        payoutRequest.get(5, TimeUnit.SECONDS);

        PayoutTransaction afterProviderResponse = payoutTransactionRepository.findById(scenario.payoutId()).orElseThrow();
        assertThat(afterProviderResponse.getProviderDispatchIntentAt()).isNotNull();
        assertThat(afterProviderResponse.getProviderRef()).isEqualTo("synthetic-completed-ref");
        assertThat(afterProviderResponse.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(afterProviderResponse.getFailureCode())
                .isEqualTo("PAYOUT_MANUAL_RECONCILIATION_REQUIRED");
        assertThat(afterProviderResponse.getNextRetryAt()).isNull();

        payoutService.retryPayout(scenario.payoutId());

        verify(gateway, times(1)).requestPayout(any(PayoutGateway.PayoutRequest.class));
        verify(gateway, never()).getPayoutStatus(any());
    }

    @Test
    void applicantDeletionLockWinningBlocksAmbiguousRetryBeforeProviderRequest() throws Exception {
        Scenario scenario = createScenario("applicant-delete-wins");
        CountDownLatch applicationLocked = new CountDownLatch(1);
        CountDownLatch releaseApplicationLock = new CountDownLatch(1);
        int holderSessionId = holdApplicationLock(scenario, applicationLocked, releaseApplicationLock);

        Future<?> deletion = executor.submit(() -> deleteApplicant(scenario));
        int deletionSessionId = awaitBlockedSession(holderSessionId);
        PayoutGateway gateway = syntheticGateway();
        PayoutService payoutService = newPayoutService(gateway);
        Future<?> retry = executor.submit(() -> payoutService.retryPayout(scenario.payoutId()));

        try {
            awaitBlockedSession(deletionSessionId);
        } finally {
            releaseApplicationLock.countDown();
        }
        deletion.get(5, TimeUnit.SECONDS);
        retry.get(5, TimeUnit.SECONDS);

        PartyApplication deletedApplication = partyApplicationRepository.findById(scenario.applicationId())
                .orElseThrow();
        PayoutTransaction savedPayout = payoutTransactionRepository.findById(scenario.payoutId()).orElseThrow();
        assertThat(deletedApplication.getIsApproved()).isFalse();
        assertThat(deletedApplication.getIsRejected()).isTrue();
        assertThat(savedPayout.getProviderDispatchIntentAt()).isNull();
        assertThat(savedPayout.getFailureCode()).isEqualTo("PAYOUT_MANUAL_RECONCILIATION_REQUIRED");
        assertThat(savedPayout.getNextRetryAt()).isNull();
        verify(gateway, never()).requestPayout(any(PayoutGateway.PayoutRequest.class));
    }

    @Test
    void retryDispatchIntentWinningApplicantDeletionAllowsOnlyThatProviderRequest() throws Exception {
        Scenario scenario = createScenario("retry-dispatch-wins");
        CountDownLatch applicationLocked = new CountDownLatch(1);
        CountDownLatch releaseApplicationLock = new CountDownLatch(1);
        int holderSessionId = holdApplicationLock(scenario, applicationLocked, releaseApplicationLock);
        PayoutGateway gateway = syntheticGateway();
        when(gateway.requestPayout(any(PayoutGateway.PayoutRequest.class)))
                .thenReturn(new PayoutGateway.PayoutResult("synthetic-provider-ref", "REQUESTED"));
        when(gateway.getPayoutStatus("synthetic-provider-ref"))
                .thenReturn(new PayoutGateway.PayoutStatusResult(
                        "synthetic-provider-ref", "REQUESTED", null, null));
        PayoutService payoutService = newPayoutService(gateway);

        Future<?> retry = executor.submit(() -> payoutService.retryPayout(scenario.payoutId()));
        int retrySessionId = awaitBlockedSession(holderSessionId);
        Future<?> deletion = executor.submit(() -> deleteApplicant(scenario));

        try {
            awaitBlockedSession(retrySessionId);
        } finally {
            releaseApplicationLock.countDown();
        }
        retry.get(5, TimeUnit.SECONDS);
        deletion.get(5, TimeUnit.SECONDS);

        PartyApplication deletedApplication = partyApplicationRepository.findById(scenario.applicationId())
                .orElseThrow();
        PayoutTransaction savedPayout = payoutTransactionRepository.findById(scenario.payoutId()).orElseThrow();
        assertThat(deletedApplication.getIsApproved()).isFalse();
        assertThat(deletedApplication.getIsRejected()).isTrue();
        assertThat(savedPayout.getProviderDispatchIntentAt()).isNotNull();
        assertThat(savedPayout.getProviderRef()).isEqualTo("synthetic-provider-ref");
        verify(gateway, times(1)).requestPayout(any(PayoutGateway.PayoutRequest.class));

        inTransaction(() -> {
            PayoutTransaction duePayout = payoutTransactionRepository.findByIdForUpdate(scenario.payoutId())
                    .orElseThrow();
            duePayout.setNextRetryAt(java.time.Instant.now().minusSeconds(1));
            payoutTransactionRepository.saveAndFlush(duePayout);
            return null;
        });
        payoutService.retryPayout(scenario.payoutId());

        verify(gateway, times(1)).requestPayout(any(PayoutGateway.PayoutRequest.class));
        verify(gateway).getPayoutStatus("synthetic-provider-ref");
    }

    @Test
    void ambiguousRequestWithExistingDispatchIntentAndNoProviderRefIsHeldWithoutRedispatch() {
        Scenario scenario = createScenario("ambiguous-dispatch-response");
        PayoutGateway gateway = syntheticGateway();
        when(gateway.requestPayout(any(PayoutGateway.PayoutRequest.class)))
                .thenThrow(new IllegalStateException("synthetic ambiguous provider response"))
                .thenReturn(new PayoutGateway.PayoutResult("unexpected-second-ref", "REQUESTED"));
        PayoutService payoutService = newPayoutService(gateway);

        payoutService.retryPayout(scenario.payoutId());

        PayoutTransaction firstAttempt = payoutTransactionRepository.findById(scenario.payoutId()).orElseThrow();
        assertThat(firstAttempt.getProviderDispatchIntentAt()).isNotNull();
        assertThat(firstAttempt.getProviderRef()).isNull();
        assertThat(firstAttempt.getNextRetryAt()).isNotNull();
        verify(gateway, times(1)).requestPayout(any(PayoutGateway.PayoutRequest.class));

        inTransaction(() -> {
            PayoutTransaction duePayout = payoutTransactionRepository.findByIdForUpdate(scenario.payoutId())
                    .orElseThrow();
            duePayout.setNextRetryAt(java.time.Instant.now().minusSeconds(1));
            payoutTransactionRepository.saveAndFlush(duePayout);
            return null;
        });
        payoutService.retryPayout(scenario.payoutId());

        PayoutTransaction heldPayout = payoutTransactionRepository.findById(scenario.payoutId()).orElseThrow();
        assertThat(heldPayout.getProviderDispatchIntentAt()).isNotNull();
        assertThat(heldPayout.getProviderRef()).isNull();
        assertThat(heldPayout.getFailureCode())
                .isEqualTo("PAYOUT_MANUAL_RECONCILIATION_REQUIRED");
        assertThat(heldPayout.getNextRetryAt()).isNull();
        verify(gateway, times(1)).requestPayout(any(PayoutGateway.PayoutRequest.class));
    }

    @Test
    void expiryRecordsMismatchedPaymentApplicationLinkAsAnInternalCase() {
        Scenario scenario = createScenario("reconciliation-expiry");
        inTransaction(() -> {
            PaymentTransaction payment = paymentTransactionRepository.findById(scenario.paymentId()).orElseThrow();
            payment.setApplicationId(scenario.applicationId() + 10_000L);
            paymentTransactionRepository.saveAndFlush(payment);
            return null;
        });

        partyLifecycleMutationService.expirePendingParty(scenario.partyId(), LocalDate.now());

        MateLinkReconciliationCase reconciliationCase = reconciliationCaseRepository.findAll().stream()
                .filter(item -> scenario.paymentId().equals(item.getPaymentTransactionId()))
                .findFirst()
                .orElseThrow();
        assertThat(reconciliationCase.getPartyId()).isEqualTo(scenario.partyId());
        assertThat(reconciliationCase.getApplicationId()).isEqualTo(scenario.applicationId());
        assertThat(reconciliationCase.getReasonCode())
                .isEqualTo(MateLinkReconciliationReasonCode.PAYMENT_APPLICATION_LINK_MISMATCH);
        assertThat(reconciliationCase.getObservedAt()).isNotNull();
    }

    @Test
    void expiryRecordsPaymentSellerThatDoesNotMatchPartyHost() {
        Scenario scenario = createScenario("reconciliation-seller");
        inTransaction(() -> {
            PaymentTransaction payment = paymentTransactionRepository.findById(scenario.paymentId()).orElseThrow();
            payment.setSellerUserId(scenario.sellerId() + 10_000L);
            paymentTransactionRepository.saveAndFlush(payment);
            return null;
        });

        partyLifecycleMutationService.expirePendingParty(scenario.partyId(), LocalDate.now());

        MateLinkReconciliationCase reconciliationCase = reconciliationCaseRepository.findAll().stream()
                .filter(item -> scenario.paymentId().equals(item.getPaymentTransactionId()))
                .findFirst()
                .orElseThrow();
        assertThat(reconciliationCase.getReasonCode())
                .isEqualTo(MateLinkReconciliationReasonCode.PAYMENT_SELLER_LINK_MISMATCH);
    }

    @Test
    void dispatchRecordsMismatchedApplicationLinkAndRemainsHeldOnRepeatedAttempt() {
        Scenario scenario = createScenario("reconciliation-dispatch");
        inTransaction(() -> {
            PaymentTransaction payment = paymentTransactionRepository.findById(scenario.paymentId()).orElseThrow();
            payment.setApplicationId(scenario.applicationId() + 10_000L);
            paymentTransactionRepository.saveAndFlush(payment);
            return null;
        });

        assertThat(payoutClaimService.claimProviderDispatchIntent(scenario.paymentId(), scenario.payoutId()))
                .isFalse();
        assertThat(payoutClaimService.claimProviderDispatchIntent(scenario.paymentId(), scenario.payoutId()))
                .isFalse();

        java.util.List<MateLinkReconciliationCase> cases = reconciliationCaseRepository.findAll().stream()
                .filter(item -> scenario.paymentId().equals(item.getPaymentTransactionId()))
                .toList();
        assertThat(cases).hasSize(1);
        assertThat(cases.getFirst().getReasonCode())
                .isEqualTo(MateLinkReconciliationReasonCode.PAYMENT_APPLICATION_LINK_MISMATCH);
        PayoutTransaction payout = payoutTransactionRepository.findById(scenario.payoutId()).orElseThrow();
        assertThat(payout.getFailureCode()).isEqualTo("PAYOUT_MANUAL_RECONCILIATION_REQUIRED");
        assertThat(payout.getNextRetryAt()).isNull();
    }

    @Test
    void reconciliationCaseSurvivesRollbackOfObservingCaller() {
        long marker = Math.abs(System.nanoTime());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            reconciliationCaseRecorder.record(
                    marker,
                    marker + 1,
                    marker + 2,
                    MateLinkReconciliationReasonCode.PAYMENT_TRANSACTION_MISSING);
            status.setRollbackOnly();
        });

        assertThat(reconciliationCaseRepository.findAll().stream()
                .filter(item -> marker == item.getPartyId())
                .filter(item -> marker + 1 == item.getApplicationId())
                .filter(item -> marker + 2 == item.getPaymentTransactionId())
                .map(MateLinkReconciliationCase::getReasonCode))
                .containsExactly(MateLinkReconciliationReasonCode.PAYMENT_TRANSACTION_MISSING);
    }

    @Test
    void concurrentDuplicateObservationsPersistOneCaseAndAdminListIsPaged() throws Exception {
        long marker = Math.abs(System.nanoTime());
        MateLinkReconciliationReasonCode reasonCode = MateLinkReconciliationReasonCode.PAYMENT_TRANSACTION_MISSING;
        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch start = new CountDownLatch(1);
        java.util.List<Future<?>> writes = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            writes.add(executor.submit(() -> {
                ready.countDown();
                await(start);
                reconciliationCaseRecorder.record(marker, marker + 1, marker + 2, reasonCode);
            }));
        }
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        for (Future<?> write : writes) {
            write.get(5, TimeUnit.SECONDS);
        }

        assertThat(reconciliationCaseRepository.findAll().stream()
                .filter(item -> item.getPartyId().equals(marker))
                .filter(item -> item.getApplicationId().equals(marker + 1))
                .filter(item -> item.getPaymentTransactionId().equals(marker + 2)))
                .hasSize(1);

        long existingCases = reconciliationCaseRepository.count();
        reconciliationCaseRecorder.record(marker + 10, marker + 11, marker + 12, reasonCode);
        reconciliationCaseRecorder.record(marker + 20, marker + 21, marker + 22, reasonCode);
        reconciliationCaseRecorder.record(marker + 30, marker + 31, marker + 32, reasonCode);
        MateLinkReconciliationCasePageDto firstPage = reconciliationCaseAdminService.list(0, 2);
        MateLinkReconciliationCasePageDto secondPage = reconciliationCaseAdminService.list(1, 2);

        assertThat(firstPage.items()).hasSize(2);
        assertThat(firstPage.size()).isEqualTo(2);
        assertThat(firstPage.totalElements()).isEqualTo(existingCases + 3);
        assertThat(secondPage.items()).hasSize(2);
        assertThat(secondPage.totalElements()).isEqualTo(existingCases + 3);
        assertThat(reconciliationCaseAdminService.list(0, 1000).size()).isEqualTo(100);
    }

    @Test
    void reconciliationLedgerPersistsEveryDefinedReasonCode() {
        MateLinkReconciliationReasonCode[] reasonCodes = MateLinkReconciliationReasonCode.values();
        long marker = 1_000_000_000_000_000L + Math.floorMod(System.nanoTime(), 1_000_000_000L);

        for (int index = 0; index < reasonCodes.length; index++) {
            long partyId = marker + (index * 3L);
            reconciliationCaseRecorder.record(
                    partyId,
                    partyId + 1,
                    partyId + 2,
                    reasonCodes[index]);
        }

        var persistedCases = reconciliationCaseRepository.findAll().stream()
                .filter(item -> item.getPartyId() != null)
                .filter(item -> item.getPartyId() >= marker)
                .filter(item -> item.getPartyId() < marker + (reasonCodes.length * 3L))
                .toList();

        assertThat(persistedCases).hasSize(reasonCodes.length);
        assertThat(persistedCases)
                .extracting(MateLinkReconciliationCase::getReasonCode)
                .containsExactlyInAnyOrder(reasonCodes);
    }

    @Test
    void fallbackCleanupRunsAfterDurableProviderSnapshotBeforeStatusLookup() {
        Scenario scenario = createScenario("fallback-cleanup-after-snapshot");
        inTransaction(() -> {
            UserEntity seller = userRepository.findByIdForWrite(scenario.sellerId()).orElseThrow();
            seller.setEnabled(false);
            seller.setPendingDeletion(true);
            seller.setDeletionScheduledFor(null);
            userRepository.saveAndFlush(seller);

            PayoutTransaction payout = payoutTransactionRepository.findByIdForUpdate(scenario.payoutId())
                    .orElseThrow();
            payout.setProviderCode("TOSS");
            payout.setProviderSellerId(null);
            payout.setProviderRef("synthetic-existing-provider-ref");
            payout.setNextRetryAt(java.time.Instant.now().minusSeconds(1));
            payoutTransactionRepository.saveAndFlush(payout);

            sellerPayoutProfileRepository.saveAndFlush(SellerPayoutProfile.builder()
                    .userId(scenario.sellerId())
                    .provider("TOSS")
                    .providerSellerId("synthetic-fallback-seller-id")
                    .build());
            return null;
        });

        PayoutGateway gateway = mock(PayoutGateway.class);
        when(gateway.getProviderCode()).thenReturn("TOSS");
        when(gateway.getPayoutStatus("synthetic-existing-provider-ref"))
                .thenReturn(new PayoutGateway.PayoutStatusResult(
                        "synthetic-existing-provider-ref", "REQUESTED", null, null));
        SellerPayoutProfileService profileService = mock(SellerPayoutProfileService.class);
        when(profileService.getRequiredProviderSellerId(scenario.sellerId(), "TOSS"))
                .thenReturn("synthetic-fallback-seller-id");

        newPayoutService(gateway, profileService).retryPayout(scenario.payoutId());

        PayoutTransaction savedPayout = payoutTransactionRepository.findById(scenario.payoutId()).orElseThrow();
        SellerPayoutProfile savedProfile = sellerPayoutProfileRepository
                .findByUserIdAndProvider(scenario.sellerId(), "TOSS").orElseThrow();
        assertThat(savedPayout.getProviderSellerId()).isEqualTo("synthetic-fallback-seller-id");
        assertThat(savedPayout.getStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(savedProfile.getProviderSellerId()).isNull();
        verify(gateway).getPayoutStatus("synthetic-existing-provider-ref");
        verify(gateway, never()).requestPayout(any());
    }

    @Test
    void fallbackCleanupFailureKeepsH2SnapshotRequestedAndSkipsDispatch() {
        Scenario scenario = createScenario("fallback-cleanup-failure");
        inTransaction(() -> {
            PayoutTransaction payout = payoutTransactionRepository.findByIdForUpdate(scenario.payoutId())
                    .orElseThrow();
            payout.setStatus(SettlementStatus.REQUESTED);
            payout.setProviderCode("TOSS");
            payout.setProviderSellerId(null);
            payout.setProviderRef(null);
            payout.setNextRetryAt(java.time.Instant.now().minusSeconds(1));
            payout.setRetryCount(0);
            payout.setProviderDispatchIntentAt(null);
            payoutTransactionRepository.saveAndFlush(payout);
            sellerPayoutProfileRepository.saveAndFlush(SellerPayoutProfile.builder()
                    .userId(scenario.sellerId())
                    .provider("TOSS")
                    .providerSellerId("synthetic-fallback-before-failure")
                    .build());
            return null;
        });

        PayoutGateway gateway = syntheticGateway();
        AccountDeletionFinancialService cleanupService = mock(AccountDeletionFinancialService.class);
        doThrow(new IllegalStateException("synthetic cleanup failure"))
                .when(cleanupService).clearFallbackIfNoUnboundPayouts(scenario.sellerId());
        SellerPayoutProfileService profileService = mock(SellerPayoutProfileService.class);
        when(profileService.getRequiredProviderSellerId(scenario.sellerId(), "TOSS"))
                .thenReturn("synthetic-fallback-before-failure");

        newPayoutService(gateway, profileService, cleanupService).retryPayout(scenario.payoutId());

        PayoutTransaction savedPayout = payoutTransactionRepository.findById(scenario.payoutId()).orElseThrow();
        assertThat(savedPayout.getProviderCode()).isEqualTo("TOSS");
        assertThat(savedPayout.getProviderSellerId()).isEqualTo("synthetic-fallback-before-failure");
        assertThat(savedPayout.getStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(savedPayout.getFailureCode()).isEqualTo("PAYOUT_FALLBACK_CLEANUP_FAILED");
        assertThat(savedPayout.getRetryCount()).isZero();
        assertThat(savedPayout.getNextRetryAt()).isAfter(java.time.Instant.now());
        assertThat(savedPayout.getProviderDispatchIntentAt()).isNull();
        verify(cleanupService).clearFallbackIfNoUnboundPayouts(scenario.sellerId());
        verify(gateway, never()).requestPayout(any());
    }

    @Test
    void fallbackCleanupFromAfterCommitCallbackCommitsIndependently() {
        Scenario scenario = createScenario("fallback-cleanup-after-commit");
        inTransaction(() -> {
            UserEntity seller = userRepository.findByIdForWrite(scenario.sellerId()).orElseThrow();
            seller.setEnabled(false);
            seller.setPendingDeletion(true);
            seller.setDeletionScheduledFor(null);
            userRepository.saveAndFlush(seller);

            PayoutTransaction payout = payoutTransactionRepository.findByIdForUpdate(scenario.payoutId())
                    .orElseThrow();
            payout.setProviderCode("TOSS");
            payout.setProviderSellerId("synthetic-committed-seller-id");
            payoutTransactionRepository.saveAndFlush(payout);

            sellerPayoutProfileRepository.saveAndFlush(SellerPayoutProfile.builder()
                    .userId(scenario.sellerId())
                    .provider("TOSS")
                    .providerSellerId("synthetic-committed-seller-id")
                    .build());

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    accountDeletionFinancialService.clearFallbackIfNoUnboundPayouts(scenario.sellerId());
                }
            });
            return null;
        });

        SellerPayoutProfile committedProfile = sellerPayoutProfileRepository
                .findByUserIdAndProvider(scenario.sellerId(), "TOSS").orElseThrow();
        PayoutTransaction committedPayout = payoutTransactionRepository.findById(scenario.payoutId()).orElseThrow();
        assertThat(committedProfile.getProviderSellerId()).isNull();
        assertThat(committedPayout.getProviderSellerId()).isEqualTo("synthetic-committed-seller-id");
    }

    @Test
    void unboundTossPayoutQueryIncludesNullAndBlankSellerIdentifiersOnly() {
        Scenario scenario = createScenario("blank-fallback-query");
        PayoutTransaction payout = payoutTransactionRepository.findById(scenario.payoutId()).orElseThrow();
        payout.setStatus(SettlementStatus.REQUESTED);
        payout.setProviderCode("TOSS");
        payout.setProviderSellerId(null);
        payoutTransactionRepository.saveAndFlush(payout);
        assertThat(payoutTransactionRepository.existsUnboundTossPayoutForSeller(
                scenario.sellerId(), java.util.List.of(SettlementStatus.PENDING, SettlementStatus.REQUESTED)))
                .isTrue();

        payout.setProviderSellerId("");
        payoutTransactionRepository.saveAndFlush(payout);

        assertThat(payoutTransactionRepository.existsUnboundTossPayoutForSeller(
                scenario.sellerId(), java.util.List.of(SettlementStatus.PENDING, SettlementStatus.REQUESTED)))
                .isTrue();

        payout.setProviderSellerId("   ");
        payoutTransactionRepository.saveAndFlush(payout);
        assertThat(payoutTransactionRepository.existsUnboundTossPayoutForSeller(
                scenario.sellerId(), java.util.List.of(SettlementStatus.PENDING, SettlementStatus.REQUESTED)))
                .isTrue();

        payout.setProviderSellerId(null);
        payout.setProviderCode("   ");
        payoutTransactionRepository.saveAndFlush(payout);
        assertThat(payoutTransactionRepository.existsUnboundTossPayoutForSeller(
                scenario.sellerId(), java.util.List.of(SettlementStatus.PENDING, SettlementStatus.REQUESTED)))
                .isTrue();

        payout.setProviderSellerId("synthetic-bound-seller-id");
        payout.setProviderCode(" TOSS ");
        payoutTransactionRepository.saveAndFlush(payout);
        assertThat(payoutTransactionRepository.existsUnboundTossPayoutForSeller(
                scenario.sellerId(), java.util.List.of(SettlementStatus.PENDING, SettlementStatus.REQUESTED)))
                .isFalse();

        payout.setProviderSellerId("");
        payout.setProviderCode(" SIM ");
        payoutTransactionRepository.saveAndFlush(payout);
        assertThat(payoutTransactionRepository.existsUnboundTossPayoutForSeller(
                scenario.sellerId(), java.util.List.of(SettlementStatus.PENDING, SettlementStatus.REQUESTED)))
                .isFalse();

        payout.setProviderCode(" TOSS ");
        payout.setStatus(SettlementStatus.FAILED);
        payoutTransactionRepository.saveAndFlush(payout);
        assertThat(payoutTransactionRepository.existsUnboundTossPayoutForSeller(
                scenario.sellerId(), java.util.List.of(SettlementStatus.PENDING, SettlementStatus.REQUESTED)))
                .isFalse();
    }

    private Scenario createScenario(String suffix) {
        return inTransaction(() -> {
            UserEntity seller = userRepository.saveAndFlush(UserEntity.builder()
                    .uniqueId(UUID.randomUUID())
                    .handle("@dsp" + UUID.randomUUID().toString().substring(0, 8))
                    .name("Synthetic seller")
                    .email(suffix + "@synthetic.test")
                    .role("ROLE_USER")
                    .enabled(true)
                    .pendingDeletion(false)
                    .build());
            UserEntity applicant = userRepository.saveAndFlush(UserEntity.builder()
                    .uniqueId(UUID.randomUUID())
                    .handle("@app" + UUID.randomUUID().toString().substring(0, 8))
                    .name("Synthetic applicant")
                    .email(suffix + "-applicant@synthetic.test")
                    .role("ROLE_USER")
                    .enabled(false)
                    .pendingDeletion(true)
                    .deletionRequestedAt(LocalDateTime.now().minusDays(7))
                    .deletionScheduledFor(LocalDateTime.now().minusMinutes(1))
                    .build());
            Party party = partyRepository.saveAndFlush(Party.builder()
                    .hostId(seller.getId())
                    .hostName("Synthetic seller")
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
                    .description("Synthetic payout expiry test")
                    .ticketVerified(true)
                    .status(Party.PartyStatus.PENDING)
                    .build());
            String orderId = "SYNTHETIC-DISPATCH-" + suffix;
            PartyApplication application = partyApplicationRepository.saveAndFlush(PartyApplication.builder()
                    .partyId(party.getId())
                    .applicantId(applicant.getId())
                    .applicantName("Synthetic applicant")
                    .applicantBadge(Party.BadgeType.NEW)
                    .message("Synthetic approved application")
                    .depositAmount(5000)
                    .paymentType(PartyApplication.PaymentType.FULL)
                    .isPaid(true)
                    .isApproved(true)
                    .isRejected(false)
                    .orderId(orderId)
                    .paymentKey("SYNTHETIC-PK-" + suffix)
                    .build());
            PaymentTransaction payment = paymentTransactionRepository.saveAndFlush(PaymentTransaction.builder()
                    .partyId(party.getId())
                    .applicationId(application.getId())
                    .buyerUserId(900L)
                    .sellerUserId(seller.getId())
                    .flowType(PaymentFlowType.SELLING_FULL)
                    .orderId(orderId)
                    .paymentKey("SYNTHETIC-PK-" + suffix)
                    .grossAmount(5000)
                    .feeAmount(0)
                    .refundAmount(0)
                    .netAmount(5000)
                    .paymentStatus(PaymentStatus.PAID)
                    .settlementStatus(SettlementStatus.REQUESTED)
                    .buyerUserId(applicant.getId())
                    .build());
            PayoutTransaction payout = payoutTransactionRepository.saveAndFlush(PayoutTransaction.builder()
                    .paymentTransactionId(payment.getId())
                    .sellerId(seller.getId())
                    .requestedAmount(5000)
                    .status(SettlementStatus.REQUESTED)
                    .claimProtocol("SNAPSHOT_V1")
                    .providerCode("SIM")
                    .retryCount(0)
                    .nextRetryAt(java.time.Instant.now())
                    .build());
            return new Scenario(
                    seller.getId(), applicant.getId(), party.getId(), application.getId(), payment.getId(), payout.getId(), orderId);
        });
    }

    private void prepareInitialPayoutClaimScenario(Scenario scenario, boolean approved) {
        inTransaction(() -> {
            Party party = partyRepository.findByIdForUpdate(scenario.partyId()).orElseThrow();
            party.setStatus(Party.PartyStatus.SELLING);
            party.setGameDate(LocalDate.now().plusDays(1));
            partyRepository.saveAndFlush(party);

            PartyApplication application = partyApplicationRepository
                    .findByOrderIdForUpdate(scenario.orderId()).orElseThrow();
            application.setIsApproved(approved);
            application.setApprovedAt(approved ? java.time.Instant.now() : null);
            application.setIsPaid(true);
            application.setIsRejected(false);
            partyApplicationRepository.saveAndFlush(application);

            PaymentTransaction payment = paymentTransactionRepository
                    .findByIdForUpdate(scenario.paymentId()).orElseThrow();
            payment.setPaymentStatus(PaymentStatus.PAID);
            payment.setSettlementStatus(SettlementStatus.PENDING);
            paymentTransactionRepository.saveAndFlush(payment);

            PayoutTransaction payout = payoutTransactionRepository.findByIdForUpdate(scenario.payoutId())
                    .orElseThrow();
            payout.setStatus(SettlementStatus.PENDING);
            payout.setRequestedAmount(5000);
            payout.setFailureCode(null);
            payout.setFailReason(null);
            payout.setNextRetryAt(null);
            payout.setProviderRef(null);
            payout.setProviderDispatchIntentAt(null);
            payout.setRecoveryOffsetAmount(0);
            payout.setRecoveryOffsetReservedAt(null);
            payoutTransactionRepository.saveAndFlush(payout);
            return null;
        });
    }

    private void deleteApplicant(Scenario scenario) {
        inTransaction(() -> {
            userRepository.findByIdForWrite(scenario.applicantId()).orElseThrow();
            partyService.handleUserDeletion(scenario.applicantId());
            return null;
        });
    }

    private PayoutGateway syntheticGateway() {
        PayoutGateway gateway = mock(PayoutGateway.class);
        when(gateway.getProviderCode()).thenReturn("SIM");
        return gateway;
    }

    private PayoutService newPayoutService(PayoutGateway gateway) {
        return newPayoutService(gateway, mock(SellerPayoutProfileService.class));
    }

    private PayoutService newPayoutService(PayoutGateway gateway, SellerPayoutProfileService profileService) {
        return newPayoutService(gateway, profileService, accountDeletionFinancialService);
    }

    private PayoutService newPayoutService(
            PayoutGateway gateway,
            SellerPayoutProfileService profileService,
            AccountDeletionFinancialService cleanupService) {
        PayoutService payoutService = new PayoutService(
                payoutTransactionRepository,
                paymentTransactionRepository,
                cleanupService,
                profileService,
                payoutClaimService,
                payoutStateService,
                mock(JobSubmissionGateway.class),
                java.util.List.of(gateway));
        ReflectionTestUtils.setField(payoutService, "payoutEnabled", true);
        ReflectionTestUtils.setField(payoutService, "payoutProvider", "SIM");
        return payoutService;
    }

    private <T> T inTransaction(java.util.concurrent.Callable<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            try {
                return work.call();
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
                throw new IllegalStateException("timed out waiting for the synthetic transaction release");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private int holdApplicationLock(
            Scenario scenario,
            CountDownLatch applicationLocked,
            CountDownLatch releaseApplicationLock) throws Exception {
        AtomicInteger sessionId = new AtomicInteger(-1);
        Future<?> holder = executor.submit(() -> inTransaction(() -> {
            partyApplicationRepository.findByOrderIdForUpdate(scenario.orderId()).orElseThrow();
            sessionId.set(jdbcTemplate.queryForObject("SELECT SESSION_ID()", Integer.class));
            applicationLocked.countDown();
            await(releaseApplicationLock);
            return null;
        }));
        assertThat(applicationLocked.await(5, TimeUnit.SECONDS)).isTrue();
        return sessionId.get();
    }

    private int holdPartyAndApplicationLock(
            Scenario scenario,
            boolean approve,
            CountDownLatch applicationLocked,
            CountDownLatch releaseApplicationLock) throws Exception {
        AtomicInteger sessionId = new AtomicInteger(-1);
        executor.submit(() -> inTransaction(() -> {
            partyRepository.findByIdForUpdate(scenario.partyId()).orElseThrow();
            PartyApplication application = partyApplicationRepository
                    .findByOrderIdForUpdate(scenario.orderId()).orElseThrow();
            if (approve) {
                application.setIsApproved(true);
                application.setApprovedAt(java.time.Instant.now());
                partyApplicationRepository.saveAndFlush(application);
            }
            sessionId.set(jdbcTemplate.queryForObject("SELECT SESSION_ID()", Integer.class));
            applicationLocked.countDown();
            await(releaseApplicationLock);
            return null;
        }));
        assertThat(applicationLocked.await(5, TimeUnit.SECONDS)).isTrue();
        return sessionId.get();
    }

    private int awaitBlockedSession(int blockerSessionId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() < deadline) {
            java.util.List<Integer> blockedSessions = jdbcTemplate.query("""
                    SELECT SESSION_ID
                    FROM INFORMATION_SCHEMA.SESSIONS
                    WHERE BLOCKER_ID = ?
                    """, (resultSet, rowNumber) -> resultSet.getInt(1), blockerSessionId);
            if (!blockedSessions.isEmpty()) {
                return blockedSessions.get(0);
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Expected competing transaction to wait on session " + blockerSessionId);
    }

    private record Scenario(
            Long sellerId,
            Long applicantId,
            Long partyId,
            Long applicationId,
            Long paymentId,
            Long payoutId,
            String orderId) {
    }
}
