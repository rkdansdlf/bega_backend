package com.example.mate.service;

import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentIntent;
import com.example.mate.exception.TossPaymentException;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PaymentIntentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.jobrunr.scheduling.JobScheduler;
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
import static org.mockito.Mockito.times;
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
    private JobScheduler jobScheduler;

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
    void reconcileSingleIntent_treatsAlreadyCanceledCodeAsSuccess() {
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

        assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCELED);
        assertThat(intent.getCanceledAt()).isNotNull();
        verify(paymentMetricsService).recordCompensation("success");
        verify(paymentMetricsService, never()).recordCompensation("fail");
    }

    @Test
    void reconcileSingleIntent_treatsAlreadyFullyCanceledCodeAsSuccess() {
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

        assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCELED);
        assertThat(intent.getCanceledAt()).isNotNull();
        verify(paymentMetricsService).recordCompensation("success");
        verify(paymentMetricsService, never()).recordCompensation("fail");
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
