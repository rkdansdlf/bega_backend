package com.example.mate.service;

import com.example.mate.entity.PaymentTransaction;
import com.example.mate.entity.PayoutTransaction;
import com.example.mate.entity.SellerPayoutRecovery;
import com.example.mate.entity.SellerRecoveryOffsetAllocation;
import com.example.mate.entity.SellerRecoveryOffsetStatus;
import com.example.mate.entity.SellerRecoveryStatus;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.repository.PayoutTransactionRepository;
import com.example.mate.repository.SellerPayoutRecoveryRepository;
import com.example.mate.repository.SellerRecoveryOffsetAllocationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SellerRecoveryServiceTest {

    @Mock
    private SellerPayoutRecoveryRepository recoveryRepository;

    @Mock
    private PayoutTransactionRepository payoutTransactionRepository;

    @Mock
    private SellerRecoveryOffsetAllocationRepository offsetAllocationRepository;

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
        PayoutTransaction payout = PayoutTransaction.builder().id(50L).sellerId(20L).build();
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
        given(offsetAllocationRepository.findByPayoutTransactionIdAndRecoveryId(50L, 1L))
                .willReturn(Optional.empty());

        SellerRecoveryService.RecoveryOffsetResult result = sellerRecoveryService.reserveOffset(payout, 20L, 4000);

        assertThat(result.offsetAmount()).isEqualTo(4000);
        assertThat(recovery.getRecoveredAmount()).isEqualTo(1000);
        assertThat(recovery.getStatus()).isEqualTo(SellerRecoveryStatus.PARTIALLY_RECOVERED);
        verify(offsetAllocationRepository).saveAll(argThat(allocations -> {
            List<SellerRecoveryOffsetAllocation> saved = StreamSupport.stream(
                    allocations.spliterator(), false).toList();
            return saved.size() == 1
                    && saved.get(0).getAmount() == 4000
                    && saved.get(0).getStatus() == SellerRecoveryOffsetStatus.RESERVED;
        }));
    }

    @Test
    void reserveOffsetCompletesDebtAcrossRowsInStableOrder() {
        PayoutTransaction payout = PayoutTransaction.builder().id(51L).sellerId(20L).build();
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
        given(offsetAllocationRepository.findByPayoutTransactionIdAndRecoveryId(51L, 1L))
                .willReturn(Optional.empty());
        given(offsetAllocationRepository.findByPayoutTransactionIdAndRecoveryId(51L, 2L))
                .willReturn(Optional.empty());

        SellerRecoveryService.RecoveryOffsetResult result = sellerRecoveryService.reserveOffset(payout, 20L, 6000);

        assertThat(result.offsetAmount()).isEqualTo(6000);
        assertThat(first.getRecoveredAmount()).isZero();
        assertThat(first.getStatus()).isEqualTo(SellerRecoveryStatus.PENDING);
        assertThat(second.getRecoveredAmount()).isEqualTo(1000);
        assertThat(second.getStatus()).isEqualTo(SellerRecoveryStatus.PARTIALLY_RECOVERED);
        verify(offsetAllocationRepository).saveAll(argThat(allocations -> {
            List<SellerRecoveryOffsetAllocation> saved = StreamSupport.stream(
                    allocations.spliterator(), false).toList();
            return saved.size() == 2
                    && saved.get(0).getAmount() == 3000
                    && saved.get(1).getAmount() == 3000;
        }));
    }

    @Test
    void applyReservedOffsetChangesDebtOnlyAfterPayoutCompletes() {
        PayoutTransaction payout = PayoutTransaction.builder().id(52L).sellerId(20L).build();
        SellerPayoutRecovery recovery = SellerPayoutRecovery.builder()
                .id(1L)
                .sellerUserId(20L)
                .recoveryAmount(5000)
                .recoveredAmount(1000)
                .status(SellerRecoveryStatus.PARTIALLY_RECOVERED)
                .build();
        SellerRecoveryOffsetAllocation allocation = SellerRecoveryOffsetAllocation.builder()
                .id(60L)
                .payoutTransactionId(52L)
                .recoveryId(1L)
                .amount(4000)
                .status(SellerRecoveryOffsetStatus.RESERVED)
                .build();
        given(recoveryRepository.findAllBySellerUserIdForUpdate(20L)).willReturn(List.of(recovery));
        given(offsetAllocationRepository.findByPayoutTransactionIdAndStatusInForUpdate(
                52L, List.of(SellerRecoveryOffsetStatus.RESERVED))).willReturn(List.of(allocation));

        sellerRecoveryService.applyReservedOffset(payout);

        assertThat(recovery.getRecoveredAmount()).isEqualTo(5000);
        assertThat(recovery.getStatus()).isEqualTo(SellerRecoveryStatus.RECOVERED);
        assertThat(allocation.getStatus()).isEqualTo(SellerRecoveryOffsetStatus.APPLIED);
    }

    @Test
    void releaseReservedOffsetDoesNotReduceOrIncreaseRecoveredDebt() {
        PayoutTransaction payout = PayoutTransaction.builder().id(53L).sellerId(20L).build();
        SellerRecoveryOffsetAllocation allocation = SellerRecoveryOffsetAllocation.builder()
                .id(61L)
                .payoutTransactionId(53L)
                .recoveryId(1L)
                .amount(4000)
                .status(SellerRecoveryOffsetStatus.RESERVED)
                .build();
        given(recoveryRepository.findAllBySellerUserIdForUpdate(20L)).willReturn(List.of());
        given(offsetAllocationRepository.findByPayoutTransactionIdAndStatusInForUpdate(
                53L, List.of(SellerRecoveryOffsetStatus.RESERVED))).willReturn(List.of(allocation));

        sellerRecoveryService.releaseReservedOffset(payout);

        assertThat(allocation.getStatus()).isEqualTo(SellerRecoveryOffsetStatus.RELEASED);
        verify(offsetAllocationRepository).saveAll(List.of(allocation));
    }
}
