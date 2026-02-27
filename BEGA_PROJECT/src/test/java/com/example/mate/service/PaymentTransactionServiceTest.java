package com.example.mate.service;

import com.example.mate.dto.PartyApplicationDTO;
import com.example.mate.entity.CancelReasonType;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentFlowType;
import com.example.mate.entity.PaymentIntent;
import com.example.mate.entity.PaymentStatus;
import com.example.mate.entity.PaymentTransaction;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.exception.TossPaymentException;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PaymentTransactionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentTransactionServiceTest {

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private PartyApplicationRepository partyApplicationRepository;

    @Mock
    private PartyRepository partyRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private TossPaymentService tossPaymentService;

    @Mock
    private CancelPolicyService cancelPolicyService;

    @Mock
    private PayoutService payoutService;

    @Mock
    private PaymentMetricsService paymentMetricsService;
    @Mock
    private MatePaymentModeService matePaymentModeService;

    @InjectMocks
    private PaymentTransactionService paymentTransactionService;

    @Test
    void createOrGetOnConfirm_returnsExistingTransactionOnDuplicateRequest() {
        PartyApplication application = PartyApplication.builder()
                .id(101L)
                .orderId("MATE-1-10-1234")
                .partyId(1L)
                .depositAmount(35000)
                .build();

        PaymentIntent intent = PaymentIntent.builder()
                .orderId("MATE-1-10-1234")
                .flowType(PaymentFlowType.DEPOSIT)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .build();

        PaymentTransaction existing = PaymentTransaction.builder()
                .id(300L)
                .orderId(application.getOrderId())
                .paymentKey("payment_key")
                .flowType(PaymentFlowType.DEPOSIT)
                .grossAmount(35000)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.PENDING)
                .build();

        when(paymentTransactionRepository.findByOrderIdForUpdate(application.getOrderId()))
                .thenReturn(Optional.of(existing));

        PaymentTransaction actual = paymentTransactionService.createOrGetOnConfirm(application, intent, "payment_key");

        assertThat(actual).isSameAs(existing);
        verify(paymentTransactionRepository).findByOrderIdForUpdate(application.getOrderId());
        verify(paymentTransactionRepository, never()).save(existing);
    }

    @Test
    void createOrGetOnConfirm_throwsWhenAmountChanged() {
        PartyApplication application = PartyApplication.builder()
                .id(101L)
                .orderId("MATE-1-10-1234")
                .partyId(1L)
                .depositAmount(10000)
                .build();

        PaymentIntent intent = PaymentIntent.builder()
                .orderId("MATE-1-10-1234")
                .flowType(PaymentFlowType.DEPOSIT)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .build();

        PaymentTransaction existing = PaymentTransaction.builder()
                .id(300L)
                .orderId(application.getOrderId())
                .paymentKey("payment_key")
                .flowType(PaymentFlowType.DEPOSIT)
                .grossAmount(35000)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.PENDING)
                .build();

        when(paymentTransactionRepository.findByOrderIdForUpdate(application.getOrderId()))
                .thenReturn(Optional.of(existing));

        assertThrows(IllegalStateException.class, () ->
                paymentTransactionService.createOrGetOnConfirm(application, intent, "payment_key"));
        verify(paymentTransactionRepository, never()).save(existing);
    }

    @Test
    void createOrGetOnConfirm_usesPaymentKeyWhenOrderMissing() {
        PartyApplication application = PartyApplication.builder()
                .id(102L)
                .orderId("MATE-1-20-5678")
                .partyId(1L)
                .depositAmount(12000)
                .build();

        PaymentIntent intent = PaymentIntent.builder()
                .orderId("MATE-1-20-5678")
                .flowType(PaymentFlowType.DEPOSIT)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .build();

        PaymentTransaction existing = PaymentTransaction.builder()
                .id(310L)
                .orderId("MATE-1-20-5678")
                .paymentKey("payment_key_20")
                .flowType(PaymentFlowType.DEPOSIT)
                .grossAmount(12000)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.PENDING)
                .build();

        when(paymentTransactionRepository.findByOrderIdForUpdate(application.getOrderId()))
                .thenReturn(Optional.empty());
        when(paymentTransactionRepository.findByPaymentKeyForUpdate(existing.getPaymentKey()))
                .thenReturn(Optional.of(existing));

        PaymentTransaction actual = paymentTransactionService.createOrGetOnConfirm(application, intent, existing.getPaymentKey());

        assertThat(actual).isSameAs(existing);
        verify(paymentTransactionRepository).findByOrderIdForUpdate(application.getOrderId());
        verify(paymentTransactionRepository).findByPaymentKeyForUpdate(existing.getPaymentKey());
        verify(paymentTransactionRepository, never()).save(existing);
    }

    @Test
    void createOrGetOnConfirm_reusesExistingAfterDataIntegrityViolation() {
        PartyApplication application = PartyApplication.builder()
                .id(103L)
                .orderId("MATE-1-30-9999")
                .partyId(1L)
                .depositAmount(18000)
                .build();

        PaymentIntent intent = PaymentIntent.builder()
                .orderId("MATE-1-30-9999")
                .flowType(PaymentFlowType.DEPOSIT)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .build();

        PaymentTransaction existing = PaymentTransaction.builder()
                .id(401L)
                .orderId(application.getOrderId())
                .paymentKey("payment_key_30")
                .flowType(PaymentFlowType.DEPOSIT)
                .grossAmount(18000)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.PENDING)
                .build();

        given(partyRepository.findById(application.getPartyId())).willReturn(java.util.Optional.of(com.example.mate.entity.Party.builder().id(1L).build()));
        when(paymentTransactionRepository.findByOrderIdForUpdate(application.getOrderId()))
                .thenReturn(java.util.Optional.empty())
                .thenReturn(java.util.Optional.empty());
        when(paymentTransactionRepository.findByPaymentKeyForUpdate(existing.getPaymentKey()))
                .thenReturn(java.util.Optional.empty())
                .thenReturn(java.util.Optional.of(existing));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        PaymentTransaction actual = paymentTransactionService.createOrGetOnConfirm(application, intent, existing.getPaymentKey());

        assertThat(actual).isSameAs(existing);
        verify(paymentTransactionRepository, times(2)).findByOrderIdForUpdate(application.getOrderId());
        verify(paymentTransactionRepository, times(2)).findByPaymentKeyForUpdate(existing.getPaymentKey());
        verify(paymentTransactionRepository).save(any(PaymentTransaction.class));
    }

    @Test
    void createOrGetOnConfirm_throwsWhenExistingTransactionAlreadyCanceled() {
        PartyApplication application = PartyApplication.builder()
                .id(104L)
                .orderId("MATE-1-40-1111")
                .partyId(1L)
                .depositAmount(15000)
                .build();

        PaymentIntent intent = PaymentIntent.builder()
                .orderId("MATE-1-40-1111")
                .flowType(PaymentFlowType.DEPOSIT)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .build();

        PaymentTransaction existing = PaymentTransaction.builder()
                .id(402L)
                .orderId(application.getOrderId())
                .paymentKey("payment_key_40")
                .flowType(PaymentFlowType.DEPOSIT)
                .grossAmount(15000)
                .paymentStatus(PaymentStatus.CANCELED)
                .settlementStatus(SettlementStatus.PENDING)
                .build();

        when(paymentTransactionRepository.findByOrderIdForUpdate(application.getOrderId()))
                .thenReturn(java.util.Optional.of(existing));

        assertThrows(IllegalStateException.class, () ->
                paymentTransactionService.createOrGetOnConfirm(application, intent, existing.getPaymentKey()));

        verify(paymentTransactionRepository).findByOrderIdForUpdate(application.getOrderId());
        verify(paymentTransactionRepository, never()).save(any(PaymentTransaction.class));
    }

    @Test
    void requestSettlementOnApproval_marksFailedWhenPayoutFails() {
        PartyApplication application = PartyApplication.builder()
                .id(99L)
                .orderId("ORDER-1")
                .partyId(1L)
                .applicantId(10L)
                .build();

        PaymentTransaction tx = PaymentTransaction.builder()
                .id(500L)
                .orderId("ORDER-1")
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.PENDING)
                .netAmount(35000)
                .build();

        given(paymentTransactionRepository.findByOrderId(application.getOrderId()))
                .willReturn(Optional.of(tx));
        given(payoutService.requestPayout(tx)).willThrow(new RuntimeException("payout failed"));

        paymentTransactionService.requestSettlementOnApproval(application);

        assertThat(tx.getSettlementStatus()).isEqualTo(SettlementStatus.FAILED);
        verify(paymentTransactionRepository, times(2)).save(tx);
        verify(paymentMetricsService).recordPayout("fail");
        verify(payoutService).requestPayout(tx);
    }

    @Test
    void requestSettlementOnApproval_skipsWhenAlreadyCompleted() {
        PartyApplication application = PartyApplication.builder()
                .id(99L)
                .orderId("ORDER-2")
                .build();

        PaymentTransaction tx = PaymentTransaction.builder()
                .id(501L)
                .orderId("ORDER-2")
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.COMPLETED)
                .build();

        given(paymentTransactionRepository.findByOrderId(application.getOrderId()))
                .willReturn(Optional.of(tx));

        paymentTransactionService.requestSettlementOnApproval(application);

        verify(payoutService, never()).requestPayout(tx);
        verify(paymentMetricsService, never()).recordPayout("fail");
        verifyNoMoreInteractions(payoutService, paymentMetricsService);
        verify(paymentTransactionRepository).findByOrderId(application.getOrderId());
    }

    // BUG-01 검증: 정산 완료(COMPLETED) 후 환불 시 REFUNDED_AFTER_SETTLEMENT로 기록
    @Test
    void processCancellation_setsRefundedAfterSettlementWhenSettlementWasCompleted() {
        PartyApplication application = PartyApplication.builder()
                .id(200L)
                .orderId("ORDER-SETTLED-1")
                .isPaid(true)
                .build();

        PaymentTransaction tx = PaymentTransaction.builder()
                .id(600L)
                .orderId("ORDER-SETTLED-1")
                .paymentKey("pay_key_settled")
                .grossAmount(30000)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.COMPLETED)
                .build();

        given(paymentTransactionRepository.findByOrderId(application.getOrderId()))
                .willReturn(Optional.of(tx));
        given(cancelPolicyService.decide(30000, CancelReasonType.BUYER_CHANGED_MIND))
                .willReturn(new CancelPolicyService.RefundDecision(27000, 3000, "PARTIAL_REFUND_WITH_FEE"));

        PartyApplicationDTO.CancelRequest request = PartyApplicationDTO.CancelRequest.builder()
                .cancelReasonType(CancelReasonType.BUYER_CHANGED_MIND)
                .build();

        paymentTransactionService.processCancellation(application, request);

        assertThat(tx.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(tx.getSettlementStatus()).isEqualTo(SettlementStatus.REFUNDED_AFTER_SETTLEMENT);
        assertThat(tx.getRefundAmount()).isEqualTo(27000);
        assertThat(tx.getFeeAmount()).isEqualTo(3000);
        verify(tossPaymentService).cancelPayment("pay_key_settled", "메이트 취소 처리: BUYER_CHANGED_MIND", 27000);
    }

    // BUG-01 검증: 정산 미완료 상태에서 환불 시 SKIPPED로 기록
    @Test
    void processCancellation_setsSkippedWhenSettlementWasNotCompleted() {
        PartyApplication application = PartyApplication.builder()
                .id(201L)
                .orderId("ORDER-PENDING-1")
                .isPaid(true)
                .build();

        PaymentTransaction tx = PaymentTransaction.builder()
                .id(601L)
                .orderId("ORDER-PENDING-1")
                .paymentKey("pay_key_pending")
                .grossAmount(20000)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.PENDING)
                .build();

        given(paymentTransactionRepository.findByOrderId(application.getOrderId()))
                .willReturn(Optional.of(tx));
        given(cancelPolicyService.decide(20000, CancelReasonType.BUYER_CHANGED_MIND))
                .willReturn(new CancelPolicyService.RefundDecision(20000, 0, "FULL_REFUND"));

        PartyApplicationDTO.CancelRequest request = PartyApplicationDTO.CancelRequest.builder()
                .cancelReasonType(CancelReasonType.BUYER_CHANGED_MIND)
                .build();

        paymentTransactionService.processCancellation(application, request);

        assertThat(tx.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(tx.getSettlementStatus()).isEqualTo(SettlementStatus.SKIPPED);
    }

    @Test
    void processCancellation_returnsNoPaymentWhenApplicationIsNotPaid() {
        PartyApplication application = PartyApplication.builder()
                .id(300L)
                .orderId("ORDER-DIRECT-1")
                .isPaid(false)
                .build();

        PartyApplicationDTO.CancelResponse response = paymentTransactionService.processCancellation(
                application,
                PartyApplicationDTO.CancelRequest.builder()
                        .cancelReasonType(CancelReasonType.OTHER)
                        .build());

        assertThat(response.getApplicationId()).isEqualTo(300L);
        assertThat(response.getRefundPolicyApplied()).isEqualTo("NO_PAYMENT");
        assertThat(response.getRefundAmount()).isEqualTo(0);
        assertThat(response.getFeeCharged()).isEqualTo(0);
        assertThat(response.getPaymentStatus()).isNull();
        assertThat(response.getSettlementStatus()).isNull();
        verify(paymentTransactionRepository, never()).findByOrderId(any());
        verify(tossPaymentService, never()).cancelPayment(any(), any(), any());
    }

    @Test
    void requestManualPayout_directTradeMode_throwsServiceUnavailable() {
        given(matePaymentModeService.isDirectTrade()).willReturn(true);

        assertThrows(TossPaymentException.class, () -> paymentTransactionService.requestManualPayout(1L));
        verify(paymentTransactionRepository, never()).findById(any());
    }
}
