package com.example.mate.service;

import com.example.mate.entity.CancelReasonType;
import com.example.mate.entity.PaymentStatus;
import com.example.mate.entity.PaymentTransaction;
import com.example.mate.entity.PayoutTransaction;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PaymentTransactionRepository;
import com.example.mate.repository.PayoutTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentCancellationIntentServiceTest {

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private PartyRepository partyRepository;

    @Mock
    private PayoutTransactionRepository payoutTransactionRepository;

    @Mock
    private SellerRecoveryService sellerRecoveryService;

    @InjectMocks
    private PaymentCancellationIntentService intentService;

    @Test
    void prepareCommitsNewImmutableIntentBeforeProviderCall() {
        PaymentTransaction transaction = PaymentTransaction.builder()
                .id(10L)
                .grossAmount(20000)
                .paymentStatus(PaymentStatus.PAID)
                .build();
        PaymentCancellationIntentService.CancellationIntent proposed =
                new PaymentCancellationIntentService.CancellationIntent(
                        CancelReasonType.BUYER_CHANGED_MIND,
                        "최초 요청",
                        18000,
                        2000,
                        "PARTIAL_REFUND_WITH_FEE",
                        false);
        given(paymentTransactionRepository.findByIdForUpdate(10L))
                .willReturn(Optional.of(transaction));
        given(paymentTransactionRepository.saveAndFlush(any(PaymentTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        PaymentCancellationIntentService.PreparedCancellation prepared =
                intentService.prepare(transaction, proposed);

        assertThat(prepared.transaction()).isSameAs(transaction);
        assertThat(prepared.intent().existing()).isFalse();
        assertThat(transaction.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_REQUESTED);
        assertThat(transaction.getRequestedRefundAmount()).isEqualTo(18000);
        assertThat(transaction.getRequestedFeeAmount()).isEqualTo(2000);
        assertThat(transaction.getCancellationRequestedAt()).isNotNull();
        verify(paymentTransactionRepository).saveAndFlush(transaction);
    }

    @Test
    void prepareReusesExistingIntentWhenConcurrentRequestProposesDifferentValues() {
        Instant requestedAt = Instant.parse("2026-07-15T00:00:00Z");
        PaymentTransaction transaction = PaymentTransaction.builder()
                .id(11L)
                .grossAmount(20000)
                .paymentStatus(PaymentStatus.REFUND_FAILED)
                .cancelReasonType(CancelReasonType.BUYER_CHANGED_MIND)
                .cancelMemo("원본 메모")
                .requestedRefundAmount(18000)
                .requestedFeeAmount(2000)
                .refundPolicyApplied("PARTIAL_REFUND_WITH_FEE")
                .cancellationRequestedAt(requestedAt)
                .build();
        PaymentCancellationIntentService.CancellationIntent proposed =
                new PaymentCancellationIntentService.CancellationIntent(
                        CancelReasonType.SYSTEM,
                        "바꾸려는 메모",
                        20000,
                        0,
                        "FULL_REFUND",
                        false);
        given(paymentTransactionRepository.findByIdForUpdate(11L))
                .willReturn(Optional.of(transaction));
        given(paymentTransactionRepository.saveAndFlush(any(PaymentTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        PaymentCancellationIntentService.PreparedCancellation prepared =
                intentService.prepare(transaction, proposed);

        assertThat(prepared.intent().reasonType()).isEqualTo(CancelReasonType.BUYER_CHANGED_MIND);
        assertThat(prepared.intent().memo()).isEqualTo("원본 메모");
        assertThat(prepared.intent().refundAmount()).isEqualTo(18000);
        assertThat(prepared.intent().existing()).isTrue();
        assertThat(transaction.getCancellationRequestedAt()).isEqualTo(requestedAt);
    }

    @Test
    void prepareRejectsRefundWhilePayoutOutcomeIsAmbiguous() {
        PaymentTransaction transaction = PaymentTransaction.builder()
                .id(12L)
                .grossAmount(20000)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.FAILED)
                .build();
        PayoutTransaction payout = PayoutTransaction.builder()
                .paymentTransactionId(12L)
                .status(SettlementStatus.FAILED)
                .failureCode("TOSS_PAYOUT_REQUEST_FAILED")
                .build();
        PaymentCancellationIntentService.CancellationIntent proposed =
                new PaymentCancellationIntentService.CancellationIntent(
                        CancelReasonType.BUYER_CHANGED_MIND,
                        null,
                        20000,
                        0,
                        "FULL_REFUND",
                        false);
        given(paymentTransactionRepository.findByIdForUpdate(12L))
                .willReturn(Optional.of(transaction));
        given(payoutTransactionRepository.findTopByPaymentTransactionIdOrderByIdDesc(12L))
                .willReturn(Optional.of(payout));

        assertThatThrownBy(() -> intentService.prepare(transaction, proposed))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("지급 결과");

        verify(paymentTransactionRepository, never()).saveAndFlush(any(PaymentTransaction.class));
    }

    @Test
    void finalizeCancellationRechecksLatePayoutCompletionUnderLock() {
        PaymentTransaction staleCandidate = PaymentTransaction.builder()
                .id(13L)
                .grossAmount(20000)
                .netAmount(18000)
                .paymentStatus(PaymentStatus.REFUND_REQUESTED)
                .settlementStatus(SettlementStatus.FAILED)
                .build();
        PaymentTransaction current = PaymentTransaction.builder()
                .id(13L)
                .sellerUserId(30L)
                .grossAmount(20000)
                .netAmount(18000)
                .paymentStatus(PaymentStatus.REFUND_REQUESTED)
                .settlementStatus(SettlementStatus.COMPLETED)
                .build();
        PayoutTransaction completedPayout = PayoutTransaction.builder()
                .id(14L)
                .paymentTransactionId(13L)
                .requestedAmount(16000)
                .recoveryOffsetAmount(2000)
                .providerCode("TOSS")
                .providerSellerId("seller-toss-30")
                .failureCode("PAYOUT_COMPLETION_VERIFIED")
                .status(SettlementStatus.COMPLETED)
                .build();
        given(paymentTransactionRepository.findByIdForUpdate(13L)).willReturn(Optional.of(current));
        given(payoutTransactionRepository.findTopByPaymentTransactionIdForUpdateOrderByIdDesc(13L))
                .willReturn(Optional.of(completedPayout));
        given(paymentTransactionRepository.save(current)).willReturn(current);

        PaymentTransaction result = intentService.finalizeReconciledCancellation(staleCandidate, 20000);

        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(result.getSettlementStatus()).isEqualTo(SettlementStatus.REFUNDED_AFTER_SETTLEMENT);
        verify(sellerRecoveryService).recordSettledRefund(current, 18000);
    }

    @Test
    void prepareRejectsUnverifiedLegacyCompletionWithoutProviderSnapshot() {
        PaymentTransaction transaction = PaymentTransaction.builder()
                .id(17L)
                .grossAmount(20000)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.COMPLETED)
                .build();
        PayoutTransaction legacyCompleted = PayoutTransaction.builder()
                .id(18L)
                .paymentTransactionId(17L)
                .providerCode("TOSS")
                .providerSellerId("seller-toss-17")
                .status(SettlementStatus.COMPLETED)
                .build();
        PaymentCancellationIntentService.CancellationIntent proposed =
                new PaymentCancellationIntentService.CancellationIntent(
                        CancelReasonType.BUYER_CHANGED_MIND,
                        null,
                        20000,
                        0,
                        "FULL_REFUND",
                        false);
        given(paymentTransactionRepository.findByIdForUpdate(17L)).willReturn(Optional.of(transaction));
        given(payoutTransactionRepository.findTopByPaymentTransactionIdOrderByIdDesc(17L))
                .willReturn(Optional.of(legacyCompleted));

        assertThatThrownBy(() -> intentService.prepare(transaction, proposed))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("지급 결과");

        verify(paymentTransactionRepository, never()).saveAndFlush(any(PaymentTransaction.class));
    }

    @Test
    void prepareRejectsLegacySellerInvalidFailureWithoutHttpStatusSnapshot() {
        PaymentTransaction transaction = PaymentTransaction.builder()
                .id(21L)
                .grossAmount(20000)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.FAILED)
                .build();
        PayoutTransaction legacyFailed = PayoutTransaction.builder()
                .id(22L)
                .paymentTransactionId(21L)
                .status(SettlementStatus.FAILED)
                .failureCode("TOSS_SELLER_INVALID")
                .build();
        PaymentCancellationIntentService.CancellationIntent proposed =
                new PaymentCancellationIntentService.CancellationIntent(
                        CancelReasonType.BUYER_CHANGED_MIND,
                        null,
                        20000,
                        0,
                        "FULL_REFUND",
                        false);
        given(paymentTransactionRepository.findByIdForUpdate(21L)).willReturn(Optional.of(transaction));
        given(payoutTransactionRepository.findTopByPaymentTransactionIdOrderByIdDesc(21L))
                .willReturn(Optional.of(legacyFailed));

        assertThatThrownBy(() -> intentService.prepare(transaction, proposed))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("지급 결과");

        verify(paymentTransactionRepository, never()).saveAndFlush(any(PaymentTransaction.class));
    }

    @Test
    void prepareRejectsLegacyPendingPayoutWithoutClaimProtocol() {
        PaymentTransaction transaction = PaymentTransaction.builder()
                .id(23L)
                .grossAmount(20000)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.PENDING)
                .build();
        PayoutTransaction legacyPending = PayoutTransaction.builder()
                .id(24L)
                .paymentTransactionId(23L)
                .status(SettlementStatus.PENDING)
                .build();
        PaymentCancellationIntentService.CancellationIntent proposed =
                new PaymentCancellationIntentService.CancellationIntent(
                        CancelReasonType.BUYER_CHANGED_MIND,
                        null,
                        20000,
                        0,
                        "FULL_REFUND",
                        false);
        given(paymentTransactionRepository.findByIdForUpdate(23L)).willReturn(Optional.of(transaction));
        given(payoutTransactionRepository.findTopByPaymentTransactionIdOrderByIdDesc(23L))
                .willReturn(Optional.of(legacyPending));

        assertThatThrownBy(() -> intentService.prepare(transaction, proposed))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("지급 결과");

        verify(paymentTransactionRepository, never()).saveAndFlush(any(PaymentTransaction.class));
    }

    @Test
    void lateLegacyCompletionAfterProviderRefundIsCanceledButQuarantined() {
        PaymentTransaction candidate = PaymentTransaction.builder()
                .id(19L)
                .build();
        PaymentTransaction current = PaymentTransaction.builder()
                .id(19L)
                .grossAmount(20000)
                .netAmount(18000)
                .paymentStatus(PaymentStatus.REFUND_REQUESTED)
                .settlementStatus(SettlementStatus.FAILED)
                .build();
        PayoutTransaction legacyCompleted = PayoutTransaction.builder()
                .id(20L)
                .paymentTransactionId(19L)
                .requestedAmount(18000)
                .status(SettlementStatus.COMPLETED)
                .build();
        given(paymentTransactionRepository.findByIdForUpdate(19L)).willReturn(Optional.of(current));
        given(payoutTransactionRepository.findTopByPaymentTransactionIdForUpdateOrderByIdDesc(19L))
                .willReturn(Optional.of(legacyCompleted));
        given(paymentTransactionRepository.save(current)).willReturn(current);

        PaymentTransaction result = intentService.finalizeReconciledCancellation(candidate, 20000);

        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(result.getSettlementStatus()).isEqualTo(SettlementStatus.REQUESTED);
        assertThat(legacyCompleted.getFailureCode())
                .isEqualTo("PAYOUT_LEGACY_SNAPSHOT_RECONCILIATION_REQUIRED");
        verify(payoutTransactionRepository).save(legacyCompleted);
        verify(sellerRecoveryService, never()).recordSettledRefund(
                any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void markRefundFailedPreservesCurrentCompletedSettlement() {
        PaymentTransaction staleCandidate = PaymentTransaction.builder()
                .id(15L)
                .settlementStatus(SettlementStatus.FAILED)
                .build();
        PaymentTransaction current = PaymentTransaction.builder()
                .id(15L)
                .paymentStatus(PaymentStatus.REFUND_REQUESTED)
                .settlementStatus(SettlementStatus.COMPLETED)
                .build();
        given(paymentTransactionRepository.findByIdForUpdate(15L)).willReturn(Optional.of(current));
        given(paymentTransactionRepository.save(current)).willReturn(current);

        PaymentTransaction result = intentService.markRefundFailed(staleCandidate);

        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.REFUND_FAILED);
        assertThat(result.getSettlementStatus()).isEqualTo(SettlementStatus.COMPLETED);
    }

    @Test
    void lateRefundFailureCannotDowngradeCanceledPayment() {
        PaymentTransaction staleCandidate = PaymentTransaction.builder()
                .id(16L)
                .paymentStatus(PaymentStatus.REFUND_REQUESTED)
                .build();
        PaymentTransaction current = PaymentTransaction.builder()
                .id(16L)
                .paymentStatus(PaymentStatus.CANCELED)
                .settlementStatus(SettlementStatus.SKIPPED)
                .build();
        given(paymentTransactionRepository.findByIdForUpdate(16L)).willReturn(Optional.of(current));

        PaymentTransaction result = intentService.markRefundFailed(staleCandidate);

        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(result.getSettlementStatus()).isEqualTo(SettlementStatus.SKIPPED);
        verify(paymentTransactionRepository, never()).save(current);
    }

    @Test
    void prepareUsesRequiresNewTransaction() throws Exception {
        Method method = PaymentCancellationIntentService.class
                .getMethod(
                        "prepare",
                        PaymentTransaction.class,
                        PaymentCancellationIntentService.CancellationIntent.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
