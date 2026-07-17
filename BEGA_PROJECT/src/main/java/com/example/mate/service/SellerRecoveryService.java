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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SellerRecoveryService {

    private final SellerPayoutRecoveryRepository recoveryRepository;
    private final PayoutTransactionRepository payoutTransactionRepository;
    private final SellerRecoveryOffsetAllocationRepository offsetAllocationRepository;

    @Transactional
    public SellerPayoutRecovery recordSettledRefund(
            PaymentTransaction paymentTransaction,
            int fallbackOriginalPaidAmount) {
        Long paymentTransactionId = Objects.requireNonNull(
                paymentTransaction.getId(),
                "paymentTransaction.id must not be null");
        Long sellerUserId = Objects.requireNonNull(
                paymentTransaction.getSellerUserId(),
                "paymentTransaction.sellerUserId must not be null");

        SellerPayoutRecovery existing = recoveryRepository
                .findBySourcePaymentTransactionIdForUpdate(paymentTransactionId)
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        PayoutTransaction completedPayout = payoutTransactionRepository
                .findTopByPaymentTransactionIdOrderByIdDesc(paymentTransactionId)
                .filter(payout -> payout.getStatus() == SettlementStatus.COMPLETED)
                .orElse(null);
        int originalPaidAmount = completedPayout != null && completedPayout.getRequestedAmount() != null
                ? Math.max(0, completedPayout.getRequestedAmount())
                        + Math.max(0, Objects.requireNonNullElse(completedPayout.getRecoveryOffsetAmount(), 0))
                : Math.max(0, fallbackOriginalPaidAmount);
        int targetNetAmount = Math.max(0, Objects.requireNonNullElse(paymentTransaction.getNetAmount(), 0));
        int recoveryAmount = Math.max(0, originalPaidAmount - targetNetAmount);

        SellerPayoutRecovery recovery = SellerPayoutRecovery.builder()
                .sourcePaymentTransactionId(paymentTransactionId)
                .payoutTransactionId(completedPayout != null ? completedPayout.getId() : null)
                .sellerUserId(sellerUserId)
                .originalPaidAmount(originalPaidAmount)
                .targetNetAmount(targetNetAmount)
                .recoveryAmount(recoveryAmount)
                .recoveredAmount(0)
                .status(recoveryAmount == 0
                        ? SellerRecoveryStatus.RECOVERED
                        : SellerRecoveryStatus.PENDING)
                .build();
        return recoveryRepository.save(recovery);
    }

    @Transactional
    public RecoveryOffsetResult reserveOffset(
            PayoutTransaction payout,
            Long sellerUserId,
            int availableAmount) {
        if (payout == null || payout.getId() == null || sellerUserId == null || availableAmount <= 0) {
            return new RecoveryOffsetResult(0);
        }

        List<SellerPayoutRecovery> outstanding = recoveryRepository
                .findOutstandingBySellerUserIdForUpdate(sellerUserId);
        int remainingAvailable = availableAmount;
        List<SellerRecoveryOffsetAllocation> allocations = new ArrayList<>();

        for (SellerPayoutRecovery recovery : outstanding) {
            if (remainingAvailable == 0) {
                break;
            }
            int recoveryAmount = Math.max(0, Objects.requireNonNullElse(recovery.getRecoveryAmount(), 0));
            int recoveredAmount = Math.max(0, Objects.requireNonNullElse(recovery.getRecoveredAmount(), 0));
            long reservedTotal = Math.max(0L, Objects.requireNonNullElse(
                    offsetAllocationRepository.sumAmountByRecoveryIdAndStatus(
                            recovery.getId(),
                            SellerRecoveryOffsetStatus.RESERVED),
                    0L));
            int reservedAmount = (int) Math.min(Integer.MAX_VALUE, reservedTotal);
            int outstandingAmount = Math.max(0, recoveryAmount - recoveredAmount - reservedAmount);
            if (outstandingAmount == 0) {
                continue;
            }

            int applied = Math.min(remainingAvailable, outstandingAmount);
            SellerRecoveryOffsetAllocation allocation = offsetAllocationRepository
                    .findByPayoutTransactionIdAndRecoveryId(payout.getId(), recovery.getId())
                    .orElseGet(() -> SellerRecoveryOffsetAllocation.builder()
                            .payoutTransactionId(payout.getId())
                            .recoveryId(recovery.getId())
                            .build());
            allocation.setAmount(applied);
            allocation.setStatus(SellerRecoveryOffsetStatus.RESERVED);
            allocations.add(allocation);
            remainingAvailable -= applied;
        }

        if (!allocations.isEmpty()) {
            offsetAllocationRepository.saveAll(allocations);
        }
        return new RecoveryOffsetResult(availableAmount - remainingAvailable);
    }

    @Transactional
    public void applyReservedOffset(PayoutTransaction payout) {
        if (payout == null || payout.getId() == null || payout.getSellerId() == null) {
            return;
        }
        Map<Long, SellerPayoutRecovery> recoveries = recoveryRepository
                .findAllBySellerUserIdForUpdate(payout.getSellerId())
                .stream()
                .collect(Collectors.toMap(SellerPayoutRecovery::getId, Function.identity()));
        List<SellerRecoveryOffsetAllocation> allocations = offsetAllocationRepository
                .findByPayoutTransactionIdAndStatusInForUpdate(
                        payout.getId(),
                        List.of(SellerRecoveryOffsetStatus.RESERVED));
        List<SellerPayoutRecovery> changedRecoveries = new ArrayList<>();
        for (SellerRecoveryOffsetAllocation allocation : allocations) {
            SellerPayoutRecovery recovery = recoveries.get(allocation.getRecoveryId());
            if (recovery == null) {
                throw new IllegalStateException("상계 대상 판매자 회수금을 찾을 수 없습니다: " + allocation.getRecoveryId());
            }
            int recoveryAmount = Math.max(0, Objects.requireNonNullElse(recovery.getRecoveryAmount(), 0));
            int updatedRecoveredAmount = Math.min(
                    recoveryAmount,
                    Math.max(0, Objects.requireNonNullElse(recovery.getRecoveredAmount(), 0))
                            + Math.max(0, Objects.requireNonNullElse(allocation.getAmount(), 0)));
            recovery.setRecoveredAmount(updatedRecoveredAmount);
            recovery.setStatus(updatedRecoveredAmount >= recoveryAmount
                    ? SellerRecoveryStatus.RECOVERED
                    : SellerRecoveryStatus.PARTIALLY_RECOVERED);
            allocation.setStatus(SellerRecoveryOffsetStatus.APPLIED);
            changedRecoveries.add(recovery);
        }
        if (!changedRecoveries.isEmpty()) {
            recoveryRepository.saveAll(changedRecoveries);
            offsetAllocationRepository.saveAll(allocations);
        }
    }

    @Transactional
    public void releaseReservedOffset(PayoutTransaction payout) {
        if (payout == null || payout.getId() == null || payout.getSellerId() == null) {
            return;
        }
        recoveryRepository.findAllBySellerUserIdForUpdate(payout.getSellerId());
        List<SellerRecoveryOffsetAllocation> allocations = offsetAllocationRepository
                .findByPayoutTransactionIdAndStatusInForUpdate(
                        payout.getId(),
                        List.of(SellerRecoveryOffsetStatus.RESERVED));
        allocations.forEach(allocation -> allocation.setStatus(SellerRecoveryOffsetStatus.RELEASED));
        if (!allocations.isEmpty()) {
            offsetAllocationRepository.saveAll(allocations);
        }
    }

    public record RecoveryOffsetResult(int offsetAmount) {
    }
}
