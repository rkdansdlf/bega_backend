package com.example.mate.service;

import com.example.mate.dto.TossPaymentDTO;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentIntent;
import com.example.mate.exception.TossPaymentException;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PaymentIntentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jobrunr.jobs.lambdas.JobLambda;
import com.example.common.jobs.JobSubmissionGateway;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentIntentReconciliationServiceTest {

        @Mock
        private PaymentIntentRepository paymentIntentRepository;

        @Mock
        private PartyApplicationRepository applicationRepository;

        @Mock
        private TossPaymentService tossPaymentService;

        @Mock
        private PaymentMetricsService paymentMetricsService;

        @Mock
        private JobSubmissionGateway jobScheduler;

        @InjectMocks
        private PaymentIntentReconciliationService reconciliationService;

        @Test
        void reconcileSingleIntent_marksApplicationCreatedWhenApplicationExists() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(201L)
                                .orderId("MATE-1-10-8000")
                                .status(PaymentIntent.IntentStatus.CANCEL_REQUESTED)
                                .paymentKey("pay_existing_application")
                                .expectedAmount(30000)
                                .build();

                given(paymentIntentRepository.findByIdForUpdate(201L)).willReturn(java.util.Optional.of(intent));
                given(applicationRepository.findByOrderId("MATE-1-10-8000"))
                                .willReturn(java.util.Optional.of(PartyApplication.builder().id(1L).build()));

                reconciliationService.reconcileSingleIntent(201L);

                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.APPLICATION_CREATED);
                verify(paymentIntentRepository).save(intent);
                verify(tossPaymentService, never()).cancelPayment(any(), any(), anyInt());
        }

        @Test
        void reconcileSingleIntent_withoutPaymentKeyStopsWithoutCancelling() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(202L)
                                .orderId("MATE-1-10-8001")
                                .status(PaymentIntent.IntentStatus.CANCEL_FAILED)
                                .expectedAmount(15000)
                                .build();

                given(paymentIntentRepository.findByIdForUpdate(202L)).willReturn(java.util.Optional.of(intent));
                given(applicationRepository.findByOrderId("MATE-1-10-8001"))
                                .willReturn(java.util.Optional.empty());

                reconciliationService.reconcileSingleIntent(202L);

                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCEL_FAILED);
                verify(tossPaymentService, never()).cancelPayment(any(), any(), anyInt());
                verify(paymentMetricsService, never()).recordCompensation(any());
        }

        @Test
        void reconciliationHoldIsExcludedFromDirectAndScheduledCompensation() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(209L)
                                .orderId("MATE-1-10-identity-mismatch-hold")
                                .paymentKey("synthetic-request-key")
                                .expectedAmount(22000)
                                .failureCode("PROVIDER_IDENTITY_MISMATCH")
                                .status(PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED)
                                .build();
                given(paymentIntentRepository.findByIdForUpdate(209L)).willReturn(java.util.Optional.of(intent));

                reconciliationService.reconcileSingleIntent(209L);
                reconciliationService.retryCompensation(209L, 1);

                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED);
                verify(tossPaymentService, never()).cancelPayment(any(), any(), anyInt());
                verify(paymentIntentRepository, never()).save(intent);
        }

        @Test
        void verifiedPaymentRemainsHeldFromAutomaticCancellationUntilApplicationCreation() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(210L)
                                .orderId("MATE-1-10-verified-payment-pending-app")
                                .paymentKey("synthetic-admin-verified-key")
                                .expectedAmount(22000)
                                .failureCode("VERIFIED_PAYMENT_PENDING_APPLICATION")
                                .status(PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED)
                                .build();
                given(paymentIntentRepository.findByIdForUpdate(210L)).willReturn(java.util.Optional.of(intent));

                reconciliationService.reconcileSingleIntent(210L);
                reconciliationService.retryCompensation(210L, 1);

                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.RECONCILIATION_REQUIRED);
                assertThat(intent.getFailureCode()).isEqualTo("VERIFIED_PAYMENT_PENDING_APPLICATION");
                verify(tossPaymentService, never()).cancelPayment(any(), any(), anyInt());
                verify(paymentIntentRepository, never()).save(intent);
        }

        @Test
        void reconcileSingleIntent_doesNotCompleteWhenCancelResponseIsEmpty() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(206L)
                                .orderId("MATE-1-10-empty-reconcile")
                                .status(PaymentIntent.IntentStatus.CANCEL_FAILED)
                                .paymentKey("pay_empty_reconcile")
                                .expectedAmount(24000)
                                .build();

                given(paymentIntentRepository.findByIdForUpdate(206L)).willReturn(java.util.Optional.of(intent));
                given(applicationRepository.findByOrderId("MATE-1-10-empty-reconcile"))
                                .willReturn(java.util.Optional.empty());
                given(tossPaymentService.cancelPayment(eq("pay_empty_reconcile"), any(), anyInt()))
                                .willReturn(null);

                reconciliationService.reconcileSingleIntent(206L);

                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCEL_FAILED);
                assertThat(intent.getCanceledAt()).isNull();
                verify(paymentMetricsService).recordCompensation("fail");
                verify(jobScheduler).schedule(any(Instant.class), any(JobLambda.class));
        }

        @Test
        void retryCompensation_doesNotCompleteWhenProviderResponseHasNoCancellationProof() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(207L)
                                .orderId("MATE-1-10-unproven-retry")
                                .status(PaymentIntent.IntentStatus.CANCEL_FAILED)
                                .paymentKey("pay_unproven_retry")
                                .expectedAmount(18000)
                                .build();
                TossPaymentDTO.CancelResponse response = new TossPaymentDTO.CancelResponse(
                                "pay_unproven_retry", "CANCELED", 18000);

                given(paymentIntentRepository.findByIdForUpdate(207L)).willReturn(java.util.Optional.of(intent));
                given(applicationRepository.findByOrderId("MATE-1-10-unproven-retry"))
                                .willReturn(java.util.Optional.empty());
                given(tossPaymentService.cancelPayment(eq("pay_unproven_retry"), any(), anyInt()))
                                .willReturn(response);

                reconciliationService.retryCompensation(207L, 1);

                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCEL_FAILED);
                assertThat(intent.getCanceledAt()).isNull();
                verify(paymentMetricsService).recordCompensation("fail");
                verify(jobScheduler).schedule(any(Instant.class), any(JobLambda.class));
        }

        @Test
        void retryCompensation_doesNotCompleteFromAlreadyCanceledErrorWithoutCancellationProof() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(208L)
                                .orderId("MATE-1-10-already-canceled-reconcile-retry")
                                .status(PaymentIntent.IntentStatus.CANCEL_FAILED)
                                .paymentKey("pay_already_canceled_reconcile_retry")
                                .expectedAmount(18000)
                                .build();

                given(paymentIntentRepository.findByIdForUpdate(208L)).willReturn(java.util.Optional.of(intent));
                given(applicationRepository.findByOrderId("MATE-1-10-already-canceled-reconcile-retry"))
                                .willReturn(java.util.Optional.empty());
                given(tossPaymentService.cancelPayment(eq("pay_already_canceled_reconcile_retry"), any(), anyInt()))
                                .willThrow(new TossPaymentException(
                                                "결제 취소에 실패했습니다: 409 CONFLICT",
                                                HttpStatus.CONFLICT,
                                                "ALREADY_CANCELED_PAYMENT"));

                reconciliationService.retryCompensation(208L, 1);

                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCEL_FAILED);
                assertThat(intent.getCanceledAt()).isNull();
                verify(paymentMetricsService).recordCompensation("fail");
                verify(jobScheduler).schedule(any(Instant.class), any(JobLambda.class));
        }

        @Test
        void reconcileSingleIntent_doesNotCompleteFromAlreadyCanceledErrorWithoutCancellationProof() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(203L)
                                .orderId("MATE-1-10-8002")
                                .status(PaymentIntent.IntentStatus.CANCEL_FAILED)
                                .paymentKey("pay_already_cancelled")
                                .expectedAmount(22000)
                                .build();

                given(paymentIntentRepository.findByIdForUpdate(203L)).willReturn(java.util.Optional.of(intent));
                given(applicationRepository.findByOrderId("MATE-1-10-8002"))
                                .willReturn(java.util.Optional.empty());
                given(tossPaymentService.cancelPayment(eq("pay_already_cancelled"), any(), anyInt()))
                                .willThrow(new TossPaymentException(
                                                "결제 취소에 실패했습니다: 409 CONFLICT",
                                                HttpStatus.CONFLICT,
                                                "ALREADY_CANCELED_PAYMENT"));

                reconciliationService.reconcileSingleIntent(203L);

                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCEL_FAILED);
                assertThat(intent.getCanceledAt()).isNull();
                verify(paymentMetricsService).recordCompensation("fail");
                verify(jobScheduler).schedule(any(Instant.class), any(JobLambda.class));
        }

        @Test
        void reconcileSingleIntent_doesNotCompleteFromAlreadyFullyCanceledErrorWithoutCancellationProof() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(204L)
                                .orderId("MATE-1-10-8003")
                                .status(PaymentIntent.IntentStatus.CANCEL_FAILED)
                                .paymentKey("pay_already_fully_cancelled")
                                .expectedAmount(22000)
                                .build();

                given(paymentIntentRepository.findByIdForUpdate(204L)).willReturn(java.util.Optional.of(intent));
                given(applicationRepository.findByOrderId("MATE-1-10-8003"))
                                .willReturn(java.util.Optional.empty());
                given(tossPaymentService.cancelPayment(eq("pay_already_fully_cancelled"), any(), anyInt()))
                                .willThrow(new TossPaymentException(
                                                "결제 취소에 실패했습니다: 409 CONFLICT",
                                                HttpStatus.CONFLICT,
                                                "ALREADY_FULLY_CANCELED"));

                reconciliationService.reconcileSingleIntent(204L);

                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCEL_FAILED);
                assertThat(intent.getCanceledAt()).isNull();
                verify(paymentMetricsService).recordCompensation("fail");
                verify(jobScheduler).schedule(any(Instant.class), any(JobLambda.class));
        }

        @Test
        void reconcileSingleIntent_recordsFailureWhenCancelKeywordOnlyMessage() {
                PaymentIntent intent = PaymentIntent.builder()
                                .id(205L)
                                .orderId("MATE-1-10-8004")
                                .status(PaymentIntent.IntentStatus.CANCEL_FAILED)
                                .paymentKey("pay_false_cancel")
                                .expectedAmount(22000)
                                .build();

                given(paymentIntentRepository.findByIdForUpdate(205L)).willReturn(java.util.Optional.of(intent));
                given(applicationRepository.findByOrderId("MATE-1-10-8004"))
                                .willReturn(java.util.Optional.empty());
                given(tossPaymentService.cancelPayment(eq("pay_false_cancel"), any(), anyInt()))
                                .willThrow(new TossPaymentException(
                                                "결제 취소에 실패했습니다: 409 CONFLICT",
                                                HttpStatus.CONFLICT));

                reconciliationService.reconcileSingleIntent(205L);

                assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCEL_FAILED);
                verify(paymentMetricsService).recordCompensation("fail");
                verify(jobScheduler).schedule(any(Instant.class), any(JobLambda.class));
                assertThat(intent.getFailureCode()).isEqualTo("TossPaymentException");
        }
}
