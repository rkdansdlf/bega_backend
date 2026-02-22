package com.example.mate.integration;

import com.example.mate.dto.TossPaymentDTO;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentFlowType;
import com.example.mate.entity.PaymentIntent;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PaymentIntentRepository;
import com.example.mate.service.PaymentIntentService;
import com.example.mate.service.TossPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.NoTransactionException;
import org.jobrunr.scheduling.JobScheduler;

import java.sql.Timestamp;
import java.time.Instant;

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
        "spring.jwt.secret=test-jwt-secret-32-characters-long",
        "spring.jwt.refresh-expiration=86400000",
        "spring.datasource.url=jdbc:h2:mem:mate_reconciliation_integration;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=false",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=6379",
        "spring.data.redis.repositories.enabled=false",
        "jobrunr.background-job-server.enabled=false",
        "jobrunr.dashboard.enabled=false",
        "storage.type=oci",
        "oci.s3.endpoint=http://localhost:4566",
        "oci.s3.access-key=test-access-key",
        "oci.s3.secret-key=test-secret-key",
        "oci.s3.bucket=test-bucket",
        "oci.s3.region=ap-seoul-1",
        "spring.autoconfigure.exclude=io.awspring.cloud.autoconfigure.s3.S3AutoConfiguration"
})
@ExtendWith(SpringExtension.class)
class PaymentIntentReconciliationIntegrationTest {

    @Autowired
    private PaymentIntentService paymentIntentService;

    @Autowired
    private PaymentIntentRepository paymentIntentRepository;

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
                .willReturn(new TossPaymentDTO.CancelResponse("pk_reconcile_ok", "CANCELED", 31000));

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
                .applicantBadge(PartyApplication.PaymentType.DEPOSIT == PartyApplication.PaymentType.DEPOSIT
                        ? com.example.mate.entity.Party.BadgeType.NEW
                        : com.example.mate.entity.Party.BadgeType.NEW)
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
                .willReturn(new TossPaymentDTO.CancelResponse("pk_reconcile_confirmed", "CANCELED", 29000));
        given(tossPaymentService.cancelPayment(eq("pk_reconcile_cancel_requested"), any(), anyInt()))
                .willReturn(new TossPaymentDTO.CancelResponse("pk_reconcile_cancel_requested", "CANCELED", 30000));
        given(tossPaymentService.cancelPayment(eq("pk_reconcile_cancel_failed"), any(), anyInt()))
                .willReturn(new TossPaymentDTO.CancelResponse("pk_reconcile_cancel_failed", "CANCELED", 31000));

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
}
