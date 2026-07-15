package com.example.mate.service;

import com.example.mate.entity.PaymentTransaction;
import com.example.mate.entity.PayoutTransaction;
import com.example.mate.entity.SellerPayoutRecovery;
import com.example.mate.entity.SellerRecoveryStatus;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.repository.PayoutTransactionRepository;
import com.example.mate.repository.SellerPayoutRecoveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SellerRecoveryService {

    private final SellerPayoutRecoveryRepository recoveryRepository;
    private final PayoutTransactionRepository payoutTransactionRepository;

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
    public RecoveryOffsetResult reserveOffset(Long sellerUserId, int availableAmount) {
        if (sellerUserId == null || availableAmount <= 0) {
            return new RecoveryOffsetResult(0);
        }

        List<SellerPayoutRecovery> outstanding = recoveryRepository
                .findOutstandingBySellerUserIdForUpdate(sellerUserId);
        int remainingAvailable = availableAmount;
        List<SellerPayoutRecovery> changed = new ArrayList<>();

        for (SellerPayoutRecovery recovery : outstanding) {
            if (remainingAvailable == 0) {
                break;
            }
            int recoveryAmount = Math.max(0, Objects.requireNonNullElse(recovery.getRecoveryAmount(), 0));
            int recoveredAmount = Math.max(0, Objects.requireNonNullElse(recovery.getRecoveredAmount(), 0));
            int outstandingAmount = Math.max(0, recoveryAmount - recoveredAmount);
            if (outstandingAmount == 0) {
                recovery.setStatus(SellerRecoveryStatus.RECOVERED);
                changed.add(recovery);
                continue;
            }

            int applied = Math.min(remainingAvailable, outstandingAmount);
            int updatedRecoveredAmount = recoveredAmount + applied;
            recovery.setRecoveredAmount(updatedRecoveredAmount);
            recovery.setStatus(updatedRecoveredAmount >= recoveryAmount
                    ? SellerRecoveryStatus.RECOVERED
                    : SellerRecoveryStatus.PARTIALLY_RECOVERED);
            changed.add(recovery);
            remainingAvailable -= applied;
        }

        if (!changed.isEmpty()) {
            recoveryRepository.saveAll(changed);
        }
        return new RecoveryOffsetResult(availableAmount - remainingAvailable);
    }

    public record RecoveryOffsetResult(int offsetAmount) {
    }
}
