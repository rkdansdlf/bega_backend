package com.example.mate.service;

import com.example.mate.entity.PaymentTransaction;
import com.example.mate.entity.PayoutTransaction;
import com.example.mate.entity.SellerPayoutRecovery;
import com.example.mate.entity.SellerRecoveryStatus;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.repository.PayoutTransactionRepository;
import com.example.mate.repository.SellerPayoutRecoveryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SellerRecoveryServiceTest {

    @Mock
    private SellerPayoutRecoveryRepository recoveryRepository;

    @Mock
    private PayoutTransactionRepository payoutTransactionRepository;

    @InjectMocks
    private SellerRecoveryService sellerRecoveryService;

    @Test
    void recordSettledRefundCreatesDebtFromPaidAmountToRemainingEntitlement() {
        PaymentTransaction payment = PaymentTransaction.builder()
                .id(10L)
                .sellerUserId(20L)
                .netAmount(3000)
                .build();
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(30L)
                .paymentTransactionId(10L)
                .sellerId(20L)
                .requestedAmount(30000)
                .status(SettlementStatus.COMPLETED)
                .build();

        given(recoveryRepository.findBySourcePaymentTransactionIdForUpdate(10L))
                .willReturn(Optional.empty());
        given(payoutTransactionRepository.findTopByPaymentTransactionIdOrderByIdDesc(10L))
                .willReturn(Optional.of(payout));
        given(recoveryRepository.save(any(SellerPayoutRecovery.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        SellerPayoutRecovery recovery = sellerRecoveryService.recordSettledRefund(payment, 30000);

        assertThat(recovery.getSourcePaymentTransactionId()).isEqualTo(10L);
        assertThat(recovery.getPayoutTransactionId()).isEqualTo(30L);
        assertThat(recovery.getSellerUserId()).isEqualTo(20L);
        assertThat(recovery.getOriginalPaidAmount()).isEqualTo(30000);
        assertThat(recovery.getTargetNetAmount()).isEqualTo(3000);
        assertThat(recovery.getRecoveryAmount()).isEqualTo(27000);
        assertThat(recovery.getRecoveredAmount()).isZero();
        assertThat(recovery.getStatus()).isEqualTo(SellerRecoveryStatus.PENDING);
    }

    @Test
    void recordSettledRefundIncludesPreviouslyReservedRecoveryOffset() {
        PaymentTransaction payment = PaymentTransaction.builder()
                .id(12L)
                .sellerUserId(22L)
                .netAmount(0)
                .build();
        PayoutTransaction payout = PayoutTransaction.builder()
                .id(32L)
                .paymentTransactionId(12L)
                .sellerId(22L)
                .requestedAmount(6000)
                .recoveryOffsetAmount(4000)
                .status(SettlementStatus.COMPLETED)
                .build();

        given(recoveryRepository.findBySourcePaymentTransactionIdForUpdate(12L))
                .willReturn(Optional.empty());
        given(payoutTransactionRepository.findTopByPaymentTransactionIdOrderByIdDesc(12L))
                .willReturn(Optional.of(payout));
        given(recoveryRepository.save(any(SellerPayoutRecovery.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        SellerPayoutRecovery recovery = sellerRecoveryService.recordSettledRefund(payment, 10000);

        assertThat(recovery.getOriginalPaidAmount()).isEqualTo(10000);
        assertThat(recovery.getRecoveryAmount()).isEqualTo(10000);
    }

    @Test
    void recordSettledRefundReturnsExistingDebtOnRetry() {
        PaymentTransaction payment = PaymentTransaction.builder()
                .id(11L)
                .sellerUserId(21L)
                .netAmount(0)
                .build();
        SellerPayoutRecovery existing = SellerPayoutRecovery.builder()
                .id(31L)
                .sourcePaymentTransactionId(11L)
                .sellerUserId(21L)
                .recoveryAmount(15000)
                .recoveredAmount(0)
                .status(SellerRecoveryStatus.PENDING)
                .build();
        given(recoveryRepository.findBySourcePaymentTransactionIdForUpdate(11L))
                .willReturn(Optional.of(existing));

        SellerPayoutRecovery result = sellerRecoveryService.recordSettledRefund(payment, 15000);

        assertThat(result).isSameAs(existing);
        verify(recoveryRepository, never()).save(any());
        verify(payoutTransactionRepository, never())
                .findTopByPaymentTransactionIdOrderByIdDesc(any());
    }

    @Test
    void reserveOffsetPartiallyRecoversOldestDebt() {
        SellerPayoutRecovery recovery = SellerPayoutRecovery.builder()
                .id(1L)
                .sourcePaymentTransactionId(10L)
                .sellerUserId(20L)
                .recoveryAmount(10000)
                .recoveredAmount(1000)
                .status(SellerRecoveryStatus.PARTIALLY_RECOVERED)
                .build();
        given(recoveryRepository.findOutstandingBySellerUserIdForUpdate(20L))
                .willReturn(List.of(recovery));
        given(recoveryRepository.saveAll(any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        SellerRecoveryService.RecoveryOffsetResult result = sellerRecoveryService.reserveOffset(20L, 4000);

        assertThat(result.offsetAmount()).isEqualTo(4000);
        assertThat(recovery.getRecoveredAmount()).isEqualTo(5000);
        assertThat(recovery.getStatus()).isEqualTo(SellerRecoveryStatus.PARTIALLY_RECOVERED);
    }

    @Test
    void reserveOffsetCompletesDebtAcrossRowsInStableOrder() {
        SellerPayoutRecovery first = SellerPayoutRecovery.builder()
                .id(1L)
                .sellerUserId(20L)
                .recoveryAmount(3000)
                .recoveredAmount(0)
                .status(SellerRecoveryStatus.PENDING)
                .build();
        SellerPayoutRecovery second = SellerPayoutRecovery.builder()
                .id(2L)
                .sellerUserId(20L)
                .recoveryAmount(5000)
                .recoveredAmount(1000)
                .status(SellerRecoveryStatus.PARTIALLY_RECOVERED)
                .build();
        given(recoveryRepository.findOutstandingBySellerUserIdForUpdate(20L))
                .willReturn(List.of(first, second));
        given(recoveryRepository.saveAll(any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        SellerRecoveryService.RecoveryOffsetResult result = sellerRecoveryService.reserveOffset(20L, 6000);

        assertThat(result.offsetAmount()).isEqualTo(6000);
        assertThat(first.getRecoveredAmount()).isEqualTo(3000);
        assertThat(first.getStatus()).isEqualTo(SellerRecoveryStatus.RECOVERED);
        assertThat(second.getRecoveredAmount()).isEqualTo(4000);
        assertThat(second.getStatus()).isEqualTo(SellerRecoveryStatus.PARTIALLY_RECOVERED);
    }
}
