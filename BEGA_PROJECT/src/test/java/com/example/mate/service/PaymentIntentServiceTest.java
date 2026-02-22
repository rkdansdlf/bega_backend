package com.example.mate.service;

import com.example.auth.service.UserService;
import com.example.mate.dto.TossPaymentDTO;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentIntent;
import com.example.mate.exception.InvalidApplicationStatusException;
import com.example.mate.exception.TossPaymentException;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PaymentIntentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.jobrunr.scheduling.JobScheduler;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentIntentServiceTest {

    @Mock
    private PaymentIntentRepository paymentIntentRepository;

    @Mock
    private PartyApplicationRepository applicationRepository;

    @Mock
    private PartyRepository partyRepository;

    @Mock
    private PaymentAmountCalculator paymentAmountCalculator;

    @Mock
    private TossPaymentService tossPaymentService;

    @Mock
    private JobScheduler jobScheduler;

    @Mock
    private PaymentMetricsService paymentMetricsService;

    @Mock
    private UserService userService;

    @Mock
    private PaymentIntentReconciliationService paymentIntentReconciliationService;

    @InjectMocks
    private PaymentIntentService paymentIntentService;

    @Test
    void resolveIntentForConfirm_throwsWhenAmountChanged() {
        Principal principal = () -> "test@example.com";
        Long applicantId = 10L;

        TossPaymentDTO.ClientConfirmRequest request = TossPaymentDTO.ClientConfirmRequest.builder()
                .intentId(100L)
                .orderId("MATE-1-10-1000")
                .partyId(1L)
                .paymentKey("pk_test")
                .flowType(com.example.mate.entity.PaymentFlowType.DEPOSIT)
                .build();

        PaymentIntent intent = PaymentIntent.builder()
                .id(100L)
                .orderId("MATE-1-10-1000")
                .partyId(1L)
                .applicantId(applicantId)
                .expectedAmount(30000)
                .flowType(com.example.mate.entity.PaymentFlowType.DEPOSIT)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .status(PaymentIntent.IntentStatus.PREPARED)
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        Party party = Party.builder()
                .id(1L)
                .status(Party.PartyStatus.PENDING)
                .maxParticipants(6)
                .currentParticipants(1)
                .ticketPrice(10000)
                .build();

        when(paymentIntentRepository.findByIdForUpdate(100L)).thenReturn(java.util.Optional.of(intent));
        given(userService.getUserIdByEmail("test@example.com")).willReturn(applicantId);
        given(userService.isSocialVerified(applicantId)).willReturn(true);
        given(applicationRepository.findByPartyIdAndApplicantId(1L, applicantId)).willReturn(java.util.Optional.empty());
        given(applicationRepository.existsByPartyIdAndApplicantIdAndIsRejectedTrue(1L, applicantId)).willReturn(false);
        given(applicationRepository.countByPartyIdAndIsApprovedFalseAndIsRejectedFalse(1L)).willReturn(0L);
        given(partyRepository.findById(1L)).willReturn(java.util.Optional.of(party));
        given(paymentAmountCalculator.calculateAmount(1L, com.example.mate.entity.PaymentFlowType.DEPOSIT))
                .willReturn(new PaymentAmountCalculator.AmountInfo(party, 25000, "KRW", "테스트 주문"));

        assertThrows(InvalidApplicationStatusException.class,
                () -> paymentIntentService.resolveIntentForConfirm(request, principal));
    }

    @Test
    void compensateAfterApplicationFailure_skipsIfAlreadyCancelledByProvider() {
        PaymentIntent intent = PaymentIntent.builder()
                .id(101L)
                .orderId("MATE-1-10-2000")
                .applicantId(10L)
                .paymentKey("pay_cancel_1")
                .expectedAmount(25000)
                .status(PaymentIntent.IntentStatus.CONFIRMED)
                .build();

        given(tossPaymentService.cancelPayment(eq("pay_cancel_1"), any(), anyInt()))
                .willThrow(new TossPaymentException("이미 취소된 결제입니다.", HttpStatus.CONFLICT));

        paymentIntentService.compensateAfterApplicationFailure(intent, new IllegalStateException("payment failed"));

        assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCELED);
        assertThat(intent.getCanceledAt()).isNotNull();
        verify(paymentMetricsService).recordCompensation("retry");
        verify(paymentMetricsService).recordCompensation("success");
        verify(paymentIntentRepository, times(2)).save(intent);
        verify(jobScheduler, never()).schedule(any(Instant.class), any(JobLambda.class));
        verifyNoMoreInteractions(tossPaymentService);
    }

    @Test
    void compensateAfterApplicationFailure_retriesAndRecordsFailureOnCancelFailure() {
        PaymentIntent intent = PaymentIntent.builder()
                .id(102L)
                .orderId("MATE-1-10-2001")
                .applicantId(11L)
                .paymentKey("pay_cancel_2")
                .expectedAmount(27000)
                .status(PaymentIntent.IntentStatus.CONFIRMED)
                .build();

        given(tossPaymentService.cancelPayment(eq("pay_cancel_2"), any(), anyInt()))
                .willThrow(new RuntimeException("cancel failed"));

        paymentIntentService.compensateAfterApplicationFailure(intent, new IllegalStateException("payment failed"));

        assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCEL_FAILED);
        assertThat(intent.getFailureCode()).isEqualTo("RuntimeException");
        assertThat(intent.getFailureMessage()).isEqualTo("cancel failed");
        verify(paymentMetricsService).recordCompensation("fail");
        verify(jobScheduler).schedule(any(Instant.class), any(JobLambda.class));
    }

    @Test
    void retryCompensation_marksApplicationCreatedWhenApplicationAlreadyExists() {
        PaymentIntent intent = PaymentIntent.builder()
                .id(103L)
                .orderId("MATE-1-10-3000")
                .applicantId(12L)
                .paymentKey("pay_retry_1")
                .expectedAmount(30000)
                .status(PaymentIntent.IntentStatus.CANCEL_FAILED)
                .build();

        given(paymentIntentRepository.findByIdForUpdate(103L)).willReturn(java.util.Optional.of(intent));
        given(applicationRepository.findByOrderId("MATE-1-10-3000"))
                .willReturn(java.util.Optional.of(PartyApplication.builder().id(3L).build()));

        paymentIntentService.retryCompensation(103L, 1);

        assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.APPLICATION_CREATED);
        verify(paymentIntentRepository).save(intent);
        verify(tossPaymentService, never()).cancelPayment(any(), any(), anyInt());
    }

    @Test
    void retryCompensation_marksFailureAfterMaxAttempts() {
        PaymentIntent intent = PaymentIntent.builder()
                .id(104L)
                .orderId("MATE-1-10-3001")
                .applicantId(13L)
                .paymentKey("pay_retry_2")
                .expectedAmount(31000)
                .status(PaymentIntent.IntentStatus.CANCEL_FAILED)
                .build();

        given(paymentIntentRepository.findByIdForUpdate(104L)).willReturn(java.util.Optional.of(intent));

        paymentIntentService.retryCompensation(104L, 10);

        assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCEL_FAILED);
        assertThat(intent.getFailureCode()).isEqualTo("MAX_RETRY_REACHED");
        verify(paymentMetricsService).recordCompensation("fail");
    }

    // BUG-02 검증: Toss 정확한 에러 코드(ALREADY_CANCELED_PAYMENT)로 이미 취소된 결제를 판별
    @Test
    void compensateAfterApplicationFailure_skipsIfTossErrorCodeIndicatesAlreadyCancelled() {
        PaymentIntent intent = PaymentIntent.builder()
                .id(105L)
                .orderId("MATE-1-10-4000")
                .applicantId(10L)
                .paymentKey("pay_already_cancelled")
                .expectedAmount(25000)
                .status(PaymentIntent.IntentStatus.CONFIRMED)
                .build();

        given(tossPaymentService.cancelPayment(eq("pay_already_cancelled"), any(), anyInt()))
                .willThrow(new TossPaymentException(
                        "결제 취소에 실패했습니다: 409 CONFLICT",
                        HttpStatus.CONFLICT,
                        "ALREADY_CANCELED_PAYMENT"));

        paymentIntentService.compensateAfterApplicationFailure(intent, new IllegalStateException("payment failed"));

        assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCELED);
        assertThat(intent.getCanceledAt()).isNotNull();
        verify(paymentMetricsService).recordCompensation("retry");
        verify(paymentMetricsService).recordCompensation("success");
        verify(paymentIntentRepository, times(2)).save(intent);
        verify(jobScheduler, never()).schedule(any(Instant.class), any(JobLambda.class));
    }

    // BUG-02 검증: 409이지만 에러 코드가 없고 메시지에 "취소"만 포함된 경우 CANCEL_FAILED로 기록(false positive 방지)
    @Test
    void compensateAfterApplicationFailure_recordsFailureWhenCancelKeywordAloneIn409() {
        PaymentIntent intent = PaymentIntent.builder()
                .id(106L)
                .orderId("MATE-1-10-5000")
                .applicantId(10L)
                .paymentKey("pay_false_cancel")
                .expectedAmount(25000)
                .status(PaymentIntent.IntentStatus.CONFIRMED)
                .build();

        // 에러 메시지에 "취소"만 포함되고, tossErrorCode가 없으며 status도 409인 경우
        // → isAlreadyCancelledByProvider가 false를 반환해야 함(false positive 없음)
        given(tossPaymentService.cancelPayment(eq("pay_false_cancel"), any(), anyInt()))
                .willThrow(new TossPaymentException(
                        "결제 취소에 실패했습니다: 409 CONFLICT",
                        HttpStatus.CONFLICT));

        paymentIntentService.compensateAfterApplicationFailure(intent, new IllegalStateException("payment failed"));

        assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCEL_FAILED);
        verify(paymentMetricsService).recordCompensation("fail");
        verify(jobScheduler).schedule(any(Instant.class), any(JobLambda.class));
    }

    // BUG-02 검증: ALREADY_FULLY_CANCELED 에러 코드도 이미 취소된 결제로 판별
    @Test
    void compensateAfterApplicationFailure_skipsIfTossErrorCodeIsAlreadyFullyCanceled() {
        PaymentIntent intent = PaymentIntent.builder()
                .id(107L)
                .orderId("MATE-1-10-6000")
                .applicantId(10L)
                .paymentKey("pay_fully_cancelled")
                .expectedAmount(25000)
                .status(PaymentIntent.IntentStatus.CONFIRMED)
                .build();

        given(tossPaymentService.cancelPayment(eq("pay_fully_cancelled"), any(), anyInt()))
                .willThrow(new TossPaymentException(
                        "결제 취소에 실패했습니다: 409 CONFLICT",
                        HttpStatus.CONFLICT,
                        "ALREADY_FULLY_CANCELED"));

        paymentIntentService.compensateAfterApplicationFailure(intent, new IllegalStateException("payment failed"));

        assertThat(intent.getStatus()).isEqualTo(PaymentIntent.IntentStatus.CANCELED);
        assertThat(intent.getCanceledAt()).isNotNull();
        verify(paymentMetricsService).recordCompensation("success");
    }

    @Test
    void reconcileCompensationTargets_delegatesToReconciliationService() {
        PaymentIntent targetA = PaymentIntent.builder()
                .id(108L)
                .orderId("MATE-1-10-7000")
                .build();
        PaymentIntent targetB = PaymentIntent.builder()
                .id(109L)
                .orderId("MATE-1-10-7001")
                .build();

        given(paymentIntentRepository.findByStatusInAndUpdatedAtBefore(
                anySet(),
                any(Instant.class)))
                .willReturn(List.of(targetA, targetB));

        paymentIntentService.reconcileCompensationTargets();

        verify(paymentIntentRepository).findByStatusInAndUpdatedAtBefore(
                anySet(),
                any(Instant.class));
        verify(paymentIntentReconciliationService).reconcileSingleIntent(108L);
        verify(paymentIntentReconciliationService).reconcileSingleIntent(109L);
    }

    @Test
    void reconcileCompensationTargets_continuesWhenSingleTargetFails() {
        PaymentIntent targetA = PaymentIntent.builder()
                .id(110L)
                .orderId("MATE-1-10-7002")
                .build();
        PaymentIntent targetB = PaymentIntent.builder()
                .id(111L)
                .orderId("MATE-1-10-7003")
                .build();

        given(paymentIntentRepository.findByStatusInAndUpdatedAtBefore(
                anySet(),
                any(Instant.class)))
                .willReturn(List.of(targetA, targetB));

        doThrow(new RuntimeException("reconcile failed"))
                .when(paymentIntentReconciliationService).reconcileSingleIntent(110L);

        assertDoesNotThrow(() -> paymentIntentService.reconcileCompensationTargets());

        verify(paymentIntentReconciliationService).reconcileSingleIntent(110L);
        verify(paymentIntentReconciliationService).reconcileSingleIntent(111L);
    }
}
