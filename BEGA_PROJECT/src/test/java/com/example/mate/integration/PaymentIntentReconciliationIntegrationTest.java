package com.example.mate.integration;

import com.example.admin.dto.PaymentIntentReconciliationResolutionRequest;
import com.example.admin.service.PaymentIntentReconciliationAdminService;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.mate.dto.TossPaymentDTO;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentFlowType;
import com.example.mate.entity.PaymentIntent;
import com.example.mate.entity.PaymentIntentReconciliationEvent;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PaymentIntentRepository;
import com.example.mate.repository.PaymentIntentReconciliationEventRepository;
import com.example.mate.service.PaymentIntentService;
import com.example.mate.service.PaymentIntentReconciliationService;
import com.example.mate.service.TossPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.NoTransactionException;
import org.jobrunr.scheduling.JobScheduler;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.jwt.secret=test-jwt-secret-64-characters-long-for-hs512-signature-tests-key-1234567890",
        "spring.jwt.refresh-expiration=86400000",
        "spring.datasource.url=jdbc:h2:mem:mate_reconciliation_integration;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=false",
        "jobrunr.background-job-server.enabled=false",
        "jobrunr.dashboard.enabled=false",
        "storage.type=oci",
        "oci.s3.endpoint=http://localhost:4566",
        "oci.s3.access-key=test-access-key",
        "oci.s3.secret-key=test-secret-key",
        "oci.s3.bucket=test-bucket",
        "oci.s3.region=ap-seoul-1"
})
class PaymentIntentReconciliationIntegrationTest {

    @Autowired
    private PaymentIntentService paymentIntentService;

    @Autowired
    private PaymentIntentReconciliationAdminService reconciliationAdminService;

    @Autowired
    private PaymentIntentReconciliationService reconciliationService;

    @Autowired
    private PaymentIntentRepository paymentIntentRepository;

    @Autowired
    private PaymentIntentReconciliationEventRepository reconciliationEventRepository;

    @Autowired
    private PartyApplicationRepository applicationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private TossPaymentService tossPaymentService;

    @MockitoBean
    private JobScheduler jobScheduler;

    @MockitoBean
    private CheerPostRepo cheerPostRepo;

    @MockitoBean
    private CheerReportRepo cheerReportRepo;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
        paymentIntentRepository.deleteAll();
    }

    @Test
    void reconcileCompensationTargets_reconciliationUpdatesSurviveOuterRollbackWhenUsingRequiresNew() {
        PaymentIntent failureTarget = paymentIntentRepository.save(PaymentIntent.builder()
                .orderId("MATE-RECON-ROLLBACK-1")
                .partyId(101L)
                .applicantId(201L)
                .expectedAmount(30000)
                .currency("KRW")
                .flowType(PaymentFlowType.DEPOSIT)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .mode(PaymentIntent.IntentMode.PREPARED)
                .status(PaymentIntent.IntentStatus.CANCEL_REQUESTED)
                .paymentKey("pk_reconcile_fail")
                .build());

        PaymentIntent successTarget = paymentIntentRepository.save(PaymentIntent.builder()
                .orderId("MATE-RECON-ROLLBACK-2")
                .partyId(102L)
                .applicantId(202L)
                .expectedAmount(31000)
                .currency("KRW")
                .flowType(PaymentFlowType.DEPOSIT)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .mode(PaymentIntent.IntentMode.PREPARED)
                .status(PaymentIntent.IntentStatus.CANCEL_REQUESTED)
                .paymentKey("pk_reconcile_ok")
                .build());

        Instant targetUpdatedAt = Instant.now().minusSeconds(120);
        markAsOld(failureTarget.getId(), targetUpdatedAt);
        markAsOld(successTarget.getId(), targetUpdatedAt);

        given(tossPaymentService.cancelPayment(eq("pk_reconcile_fail"), any(), anyInt()))
                .willThrow(new RuntimeException("provider temporary fail"));
        given(tossPaymentService.cancelPayment(eq("pk_reconcile_ok"), any(), anyInt()))
                .willReturn(verifiedCancellation("pk_reconcile_ok", 31000));

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
                    paymentIntentService.reconcileCompensationTargets();
                    throw new NoTransactionException("rollback outer tx");
                }))
                .isInstanceOf(NoTransactionException.class)
                .hasMessage("rollback outer tx");

        PaymentIntent reconciledFailure = paymentIntentRepository.findById(failureTarget.getId()).orElseThrow();
        PaymentIntent reconciledSuccess = paymentIntentRepository.findById(successTarget.getId()).orElseThrow();

        assertThat(reconciledFailure.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCEL_FAILED);
        assertThat(reconciledFailure.getFailureCode()).isEqualTo("RuntimeException");
        assertThat(reconciledFailure.getFailureMessage()).isEqualTo("provider temporary fail");

        assertThat(reconciledSuccess.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCELED);
        assertThat(reconciledSuccess.getCanceledAt()).isNotNull();
    }

    @Test
    void identityMismatchHoldAndAuditCommitBeforeConflictIsReturned() {
        PaymentIntent intent = paymentIntentRepository.save(PaymentIntent.builder()
                .orderId("MATE-IDENTITY-HOLD-COMMIT")
                .partyId(801L)
                .applicantId(802L)
                .expectedAmount(35000)
                .currency("KRW")
                .flowType(PaymentFlowType.DEPOSIT)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .mode(PaymentIntent.IntentMode.PREPARED)
                .status(PaymentIntent.IntentStatus.PREPARED)
                .paymentKey("synthetic-request-key")
                .failureCode("CONFIRM_REQUESTED")
                .build());
        TossPaymentDTO.ConfirmResponse mismatch = new TossPaymentDTO.ConfirmResponse(
                "synthetic-candidate-key", "MATE-IDENTITY-OTHER-ORDER", "DONE", 35000, "card");

        assertThatThrownBy(() -> {
            boolean quarantined = paymentIntentService.withApplicantIntentLocked(
                    intent.getId(),
                    intent.getApplicantId(),
                    lockedIntent -> {
                        paymentIntentService.quarantineProviderIdentityMismatch(
                                lockedIntent, "synthetic-request-key", mismatch);
                        return true;
                    });
            if (quarantined) {
                throw new IllegalStateException("synthetic HTTP 409 after transactional hold commit");
            }
        }).isInstanceOf(IllegalStateException.class)
                .hasMessage("synthetic HTTP 409 after transactional hold commit");

        PaymentIntent persisted = paymentIntentRepository.findById(intent.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED);
        assertThat(persisted.getPaymentKey()).isEqualTo("synthetic-request-key");
        assertThat(persisted.getFailureCode()).isEqualTo("PROVIDER_IDENTITY_MISMATCH");
        assertThat(reconciliationEventRepository
                .findFirstByPaymentIntentIdAndEventTypeOrderByCreatedAtAsc(
                        intent.getId(), PaymentIntentReconciliationEvent.EventType.IDENTITY_MISMATCH_HOLD))
                .get()
                .satisfies(event -> {
                    assertThat(event.getRequestedPaymentKey()).isEqualTo("synthetic-request-key");
                    assertThat(event.getCandidatePaymentKey()).isEqualTo("synthetic-candidate-key");
                    assertThat(event.getCandidateOrderId()).isEqualTo("MATE-IDENTITY-OTHER-ORDER");
                });
    }

    @Test
    void adminVerifiedPaymentRemainsOutsideAutomaticCancellationUntilApplicationCreation() {
        PaymentIntent intent = paymentIntentRepository.save(PaymentIntent.builder()
                .orderId("MATE-IDENTITY-VERIFIED-PENDING-APP")
                .partyId(811L)
                .applicantId(812L)
                .expectedAmount(36000)
                .currency("KRW")
                .flowType(PaymentFlowType.DEPOSIT)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .mode(PaymentIntent.IntentMode.PREPARED)
                .status(PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED)
                .paymentKey("synthetic-request-key")
                .failureCode("PROVIDER_IDENTITY_MISMATCH")
                .build());
        reconciliationEventRepository.save(PaymentIntentReconciliationEvent.builder()
                .paymentIntentId(intent.getId())
                .eventType(PaymentIntentReconciliationEvent.EventType.IDENTITY_MISMATCH_HOLD)
                .requestedPaymentKey("synthetic-request-key")
                .candidatePaymentKey("synthetic-candidate-key")
                .candidateOrderId("MATE-IDENTITY-UNTRUSTED-ORDER")
                .candidateAmount(36000)
                .candidateStatus("DONE")
                .build());

        PaymentIntentReconciliationResolutionRequest resolution = PaymentIntentReconciliationResolutionRequest.builder()
                .outcome(PaymentIntentReconciliationResolutionRequest.Outcome.VERIFIED_PAYMENT)
                .evidenceReference("case-verified-pending-application")
                .verifiedPaymentKey("synthetic-admin-verified-key")
                .verifiedOrderId(intent.getOrderId())
                .verifiedAmount(36000)
                .verifiedStatus("DONE")
                .build();
        assertThat(reconciliationAdminService.resolve(intent.getId(), 913L, resolution)
                .awaitingIndependentReview()).isTrue();
        assertThat(paymentIntentRepository.findById(intent.getId()).orElseThrow().getFailureCode())
                .isEqualTo("PROVIDER_IDENTITY_MISMATCH_REVIEW_PENDING");
        assertThat(reconciliationAdminService.resolve(intent.getId(), 914L, resolution)
                .awaitingIndependentReview()).isFalse();
        assertThat(reconciliationAdminService.listPending(0, 20).totalElements()).isZero();

        assertThat(paymentIntentService.hasVerifiedIdentityPaymentBinding(intent.getId())).isTrue();
        assertThat(paymentIntentRepository.findById(intent.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED);
        reconciliationService.reconcileSingleIntent(intent.getId());
        reconciliationService.retryCompensation(intent.getId(), 1);

        PaymentIntent persisted = paymentIntentRepository.findById(intent.getId()).orElseThrow();
        assertThat(persisted.getPaymentKey()).isEqualTo("synthetic-admin-verified-key");
        assertThat(persisted.getFailureCode()).isEqualTo("VERIFIED_PAYMENT_PENDING_APPLICATION");
        assertThat(persisted.getStatus()).isEqualTo(PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED);
        verify(tossPaymentService, never()).cancelPayment(any(), any(), anyInt());
    }

    @Test
    void concurrentAdministratorsCannotFinalizeOneProposalMoreThanOnce() throws Exception {
        PaymentIntent intent = paymentIntentRepository.save(PaymentIntent.builder()
                .orderId("MATE-IDENTITY-CONCURRENT-APPROVAL")
                .partyId(821L)
                .applicantId(822L)
                .expectedAmount(37000)
                .currency("KRW")
                .flowType(PaymentFlowType.DEPOSIT)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .mode(PaymentIntent.IntentMode.PREPARED)
                .status(PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED)
                .paymentKey("synthetic-request-key")
                .failureCode("PROVIDER_IDENTITY_MISMATCH")
                .build());
        reconciliationEventRepository.save(PaymentIntentReconciliationEvent.builder()
                .paymentIntentId(intent.getId())
                .eventType(PaymentIntentReconciliationEvent.EventType.IDENTITY_MISMATCH_HOLD)
                .requestedPaymentKey("synthetic-request-key")
                .build());
        PaymentIntentReconciliationResolutionRequest resolution = PaymentIntentReconciliationResolutionRequest.builder()
                .outcome(PaymentIntentReconciliationResolutionRequest.Outcome.VERIFIED_PAYMENT)
                .evidenceReference("case-concurrent-admin-review")
                .verifiedPaymentKey("synthetic-concurrently-approved-key")
                .verifiedOrderId(intent.getOrderId())
                .verifiedAmount(37000)
                .verifiedStatus("DONE")
                .build();
        assertThat(reconciliationAdminService.resolve(intent.getId(), 921L, resolution)
                .awaitingIndependentReview()).isTrue();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger finalized = new AtomicInteger();
        AtomicInteger conflicted = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> submitConcurrentApproval(
                    intent.getId(), 922L, resolution, ready, start, finalized, conflicted));
            Future<?> second = executor.submit(() -> submitConcurrentApproval(
                    intent.getId(), 923L, resolution, ready, start, finalized, conflicted));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(finalized.get()).isEqualTo(1);
        assertThat(conflicted.get()).isEqualTo(1);
        assertThat(paymentIntentRepository.findById(intent.getId()).orElseThrow().getFailureCode())
                .isEqualTo("VERIFIED_PAYMENT_PENDING_APPLICATION");
        assertThat(reconciliationEventRepository
                .findByPaymentIntentIdOrderByCreatedAtAscIdAsc(intent.getId()))
                .extracting(PaymentIntentReconciliationEvent::getEventType)
                .containsExactly(
                        PaymentIntentReconciliationEvent.EventType.IDENTITY_MISMATCH_HOLD,
                        PaymentIntentReconciliationEvent.EventType.VERIFIED_PAYMENT_PROPOSED,
                        PaymentIntentReconciliationEvent.EventType.VERIFIED_PAYMENT_BOUND);
    }

    private void submitConcurrentApproval(
            Long intentId,
            Long administratorId,
            PaymentIntentReconciliationResolutionRequest resolution,
            CountDownLatch ready,
            CountDownLatch start,
            AtomicInteger finalized,
            AtomicInteger conflicted) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("concurrent admin approval start timed out");
            }
            var result = reconciliationAdminService.resolve(intentId, administratorId, resolution);
            if (result.awaitingIndependentReview()) {
                throw new AssertionError("a matching second admin must finalize, not replace, the proposal");
            }
            finalized.incrementAndGet();
        } catch (com.example.mate.exception.TossPaymentException expectedConflict) {
            if (!"PAYMENT_INTENT_RECONCILIATION_ALREADY_RESOLVED".equals(expectedConflict.getCode())) {
                throw expectedConflict;
            }
            conflicted.incrementAndGet();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    @Test
    void reconcileCompensationTargets_skipsCancelWhenApplicationAlreadyExists() {
        PaymentIntent target = paymentIntentRepository.save(PaymentIntent.builder()
                .orderId("MATE-RECON-APP-1")
                .partyId(103L)
                .applicantId(203L)
                .expectedAmount(28000)
                .currency("KRW")
                .flowType(PaymentFlowType.DEPOSIT)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .mode(PaymentIntent.IntentMode.PREPARED)
                .status(PaymentIntent.IntentStatus.CANCEL_REQUESTED)
                .paymentKey("pk_reconcile_existing_app")
                .build());

        applicationRepository.save(PartyApplication.builder()
                .partyId(103L)
                .applicantId(203L)
                .applicantName("existing-applicant")
                .applicantBadge(com.example.mate.entity.Party.BadgeType.NEW)
                .applicantRating(5.0)
                .message("existing application")
                .depositAmount(28000)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .isPaid(true)
                .isApproved(true)
                .isRejected(false)
                .orderId("MATE-RECON-APP-1")
                .paymentKey("pk_reconcile_existing_app")
                .responseDeadline(Instant.now().plusSeconds(60))
                .build());

        markAsOld(target.getId(), Instant.now().minusSeconds(120));

        paymentIntentService.reconcileCompensationTargets();

        PaymentIntent reconciled = paymentIntentRepository.findById(target.getId()).orElseThrow();
        assertThat(reconciled.getStatus()).isEqualTo(PaymentIntent.IntentStatus.APPLICATION_CREATED);
        assertThat(reconciled.getFailureCode()).isNull();
        assertThat(reconciled.getFailureMessage()).isNull();
        assertThat(reconciled.getPaymentKey()).isEqualTo("pk_reconcile_existing_app");

        verify(tossPaymentService, never()).cancelPayment(eq("pk_reconcile_existing_app"), any(), anyInt());
    }

    @Test
    void reconcileCompensationTargets_retriesBothOldAndNewStatuses() {
        PaymentIntent confirmedTarget = paymentIntentRepository.save(PaymentIntent.builder()
                .orderId("MATE-RECON-CONFIRMED-1")
                .partyId(104L)
                .applicantId(204L)
                .expectedAmount(29000)
                .currency("KRW")
                .flowType(PaymentFlowType.DEPOSIT)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .mode(PaymentIntent.IntentMode.PREPARED)
                .status(PaymentIntent.IntentStatus.CONFIRMED)
                .paymentKey("pk_reconcile_confirmed")
                .build());

        PaymentIntent cancelRequestedTarget = paymentIntentRepository.save(PaymentIntent.builder()
                .orderId("MATE-RECON-CANCEL-REQ-1")
                .partyId(105L)
                .applicantId(205L)
                .expectedAmount(30000)
                .currency("KRW")
                .flowType(PaymentFlowType.DEPOSIT)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .mode(PaymentIntent.IntentMode.PREPARED)
                .status(PaymentIntent.IntentStatus.CANCEL_REQUESTED)
                .paymentKey("pk_reconcile_cancel_requested")
                .build());

        PaymentIntent cancelFailedTarget = paymentIntentRepository.save(PaymentIntent.builder()
                .orderId("MATE-RECON-CANCEL-FAILED-1")
                .partyId(106L)
                .applicantId(206L)
                .expectedAmount(31000)
                .currency("KRW")
                .flowType(PaymentFlowType.DEPOSIT)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .mode(PaymentIntent.IntentMode.PREPARED)
                .status(PaymentIntent.IntentStatus.CANCEL_FAILED)
                .paymentKey("pk_reconcile_cancel_failed")
                .build());

        Instant targetUpdatedAt = Instant.now().minusSeconds(120);
        markAsOld(confirmedTarget.getId(), targetUpdatedAt);
        markAsOld(cancelRequestedTarget.getId(), targetUpdatedAt);
        markAsOld(cancelFailedTarget.getId(), targetUpdatedAt);

        given(tossPaymentService.cancelPayment(eq("pk_reconcile_confirmed"), any(), anyInt()))
                .willReturn(verifiedCancellation("pk_reconcile_confirmed", 29000));
        given(tossPaymentService.cancelPayment(eq("pk_reconcile_cancel_requested"), any(), anyInt()))
                .willReturn(verifiedCancellation("pk_reconcile_cancel_requested", 30000));
        given(tossPaymentService.cancelPayment(eq("pk_reconcile_cancel_failed"), any(), anyInt()))
                .willReturn(verifiedCancellation("pk_reconcile_cancel_failed", 31000));

        paymentIntentService.reconcileCompensationTargets();

        assertThat(paymentIntentRepository.findById(confirmedTarget.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentIntent.IntentStatus.CANCELED);
        assertThat(paymentIntentRepository.findById(cancelRequestedTarget.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentIntent.IntentStatus.CANCELED);
        assertThat(paymentIntentRepository.findById(cancelFailedTarget.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentIntent.IntentStatus.CANCELED);

        verify(tossPaymentService, times(3)).cancelPayment(any(), any(), anyInt());
    }

    private void markAsOld(Long intentId, Instant instant) {
        jdbcTemplate.update("UPDATE payment_intents SET updated_at = ? WHERE id = ?", Timestamp.from(instant), intentId);
    }

    private TossPaymentDTO.CancelResponse verifiedCancellation(String paymentKey, int amount) {
        TossPaymentDTO.CancelResponse response = new TossPaymentDTO.CancelResponse(paymentKey, "DONE", amount);
        response.setCancels(List.of(new TossPaymentDTO.CancelDetail(
                amount, "DONE", Instant.now().toString(), "synthetic-reconciliation-cancel")));
        return response;
    }
}
