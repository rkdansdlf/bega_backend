package com.example.mate.service;

import com.example.mate.entity.PaymentStatus;
import com.example.mate.entity.PaymentTransaction;
import com.example.mate.entity.PayoutTransaction;
import com.example.mate.entity.SettlementStatus;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PayoutStateServiceTest {

    @Mock
    private PayoutTransactionRepository payoutTransactionRepository;
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private SellerRecoveryService sellerRecoveryService;
    @Mock
    private PaymentMetricsService metricsService;

    @InjectMocks
    private PayoutStateService stateService;

    @Test
    void staleUnknownResultCannotDowngradeTerminalFailureToRequested() {
        PaymentTransaction payment = PaymentTransaction.builder()
                .id(20L)
                .settlementStatus(SettlementStatus.FAILED)
                .build();
        PayoutTransaction terminal = PayoutTransaction.builder()
                .id(21L)
                .paymentTransactionId(20L)
                .status(SettlementStatus.FAILED)
                .failureCode("TOSS_PAYOUT_FAILED")
                .build();
        PayoutTransaction staleRequested = PayoutTransaction.builder()
                .id(21L)
                .paymentTransactionId(20L)
                .status(SettlementStatus.REQUESTED)
                .build();
        given(paymentTransactionRepository.findByIdForUpdate(20L)).willReturn(Optional.of(payment));
        given(payoutTransactionRepository.findByIdForUpdate(21L)).willReturn(Optional.of(terminal));

        PayoutTransaction result = stateService.keepRequested(
                payment,
                staleRequested,
                "provider-21",
                "TOSS_PAYOUT_STATUS_LOOKUP_FAILED",
                "timeout",
                true);

        assertThat(result).isSameAs(terminal);
        assertThat(result.getStatus()).isEqualTo(SettlementStatus.FAILED);
        verify(payoutTransactionRepository, never()).save(terminal);
        verify(paymentTransactionRepository, never()).save(payment);
    }

    @Test
    void completionAfterRefundAtomicallyCreatesSellerRecovery() {
        PaymentTransaction payment = PaymentTransaction.builder()
                .id(30L)
                .sellerUserId(40L)
                .paymentStatus(PaymentStatus.CANCELED)
                .settlementStatus(SettlementStatus.REQUESTED)
                .build();
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(31L)
                .paymentTransactionId(30L)
                .requestedAmount(18000)
                .recoveryOffsetAmount(2000)
                .status(SettlementStatus.REQUESTED)
                .build();
        given(paymentTransactionRepository.findByIdForUpdate(30L)).willReturn(Optional.of(payment));
        given(payoutTransactionRepository.findByIdForUpdate(31L)).willReturn(Optional.of(payout));

        stateService.complete(payment, payout, "provider-31");

        assertThat(payout.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(payout.getFailureCode()).isEqualTo("PAYOUT_COMPLETION_VERIFIED");
        assertThat(payment.getSettlementStatus()).isEqualTo(SettlementStatus.REFUNDED_AFTER_SETTLEMENT);
        verify(sellerRecoveryService).recordSettledRefund(payment, 20000);
    }

    @Test
    void lateCompletionCannotPromoteDefinitiveFailure() {
        PaymentTransaction payment = PaymentTransaction.builder()
                .id(40L)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.FAILED)
                .build();
        PayoutTransaction failed = PayoutTransaction.builder()
                .id(41L)
                .paymentTransactionId(40L)
                .status(SettlementStatus.FAILED)
                .failureCode("TOSS_SELLER_INVALID")
                .build();
        given(paymentTransactionRepository.findByIdForUpdate(40L)).willReturn(Optional.of(payment));
        given(payoutTransactionRepository.findByIdForUpdate(41L)).willReturn(Optional.of(failed));

        PayoutTransaction result = stateService.complete(payment, failed, "late-provider-ref");

        assertThat(result.getStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(result.getFailureCode()).isEqualTo("TOSS_SELLER_INVALID");
        verify(sellerRecoveryService, never()).applyReservedOffset(failed);
        verify(paymentTransactionRepository, never()).save(payment);
    }

    @Test
    void duplicateFailureCannotDowngradeCanceledPaymentSettlement() {
        PaymentTransaction payment = PaymentTransaction.builder()
                .id(50L)
                .paymentStatus(PaymentStatus.CANCELED)
                .settlementStatus(SettlementStatus.SKIPPED)
                .build();
        PayoutTransaction failed = PayoutTransaction.builder()
                .id(51L)
                .paymentTransactionId(50L)
                .status(SettlementStatus.FAILED)
                .failureCode("TOSS_SELLER_INVALID")
                .build();
        given(paymentTransactionRepository.findByIdForUpdate(50L)).willReturn(Optional.of(payment));
        given(payoutTransactionRepository.findByIdForUpdate(51L)).willReturn(Optional.of(failed));

        PayoutTransaction result = stateService.fail(
                payment,
                failed,
                null,
                "LATE_FAILURE",
                "late",
                true,
                false);

        assertThat(result.getFailureCode()).isEqualTo("TOSS_SELLER_INVALID");
        assertThat(payment.getSettlementStatus()).isEqualTo(SettlementStatus.SKIPPED);
        verify(paymentTransactionRepository, never()).save(payment);
    }

    @Test
    void outcomeTransitionsUseIndependentTransactions() throws Exception {
        Method complete = PayoutStateService.class.getMethod(
                "complete", PaymentTransaction.class, PayoutTransaction.class, String.class);
        Method requested = PayoutStateService.class.getMethod(
                "keepRequested",
                PaymentTransaction.class,
                PayoutTransaction.class,
                String.class,
                String.class,
                String.class,
                boolean.class);
        Method failed = PayoutStateService.class.getMethod(
                "fail",
                PaymentTransaction.class,
                PayoutTransaction.class,
                String.class,
                String.class,
                String.class,
                boolean.class,
                boolean.class);

        assertRequiresNew(complete);
        assertRequiresNew(requested);
        assertRequiresNew(failed);
    }

    private void assertRequiresNew(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
