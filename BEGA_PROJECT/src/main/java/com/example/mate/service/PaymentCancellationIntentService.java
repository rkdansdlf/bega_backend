package com.example.mate.service;

import com.example.mate.entity.CancelReasonType;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentFlowType;
import com.example.mate.entity.PaymentStatus;
import com.example.mate.entity.PaymentTransaction;
import com.example.mate.entity.PayoutTransaction;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.exception.PartyNotFoundException;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PaymentTransactionRepository;
import com.example.mate.repository.PayoutTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PaymentCancellationIntentService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PartyRepository partyRepository;
    private final PayoutTransactionRepository payoutTransactionRepository;
    private final SellerRecoveryService sellerRecoveryService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PreparedCancellation prepare(PaymentTransaction candidate, CancellationIntent proposed) {
        if (candidate == null || candidate.getId() == null) {
            throw new IllegalArgumentException("결제 트랜잭션이 올바르지 않습니다.");
        }
        PaymentTransaction transaction = paymentTransactionRepository.findByIdForUpdate(candidate.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "결제 트랜잭션을 찾을 수 없습니다: " + candidate.getId()));

        if (transaction.getPaymentStatus() == PaymentStatus.CANCELED) {
            return new PreparedCancellation(transaction, existingOrProposed(transaction, proposed));
        }

        assertPayoutOutcomeIsKnown(transaction);

        CancellationIntent persisted = existingIntent(transaction);
        if (persisted == null) {
            transaction.setCancelReasonType(proposed.reasonType());
            transaction.setCancelMemo(proposed.memo());
            transaction.setRequestedRefundAmount(proposed.refundAmount());
            transaction.setRequestedFeeAmount(proposed.feeAmount());
            transaction.setRefundPolicyApplied(proposed.policyApplied());
            transaction.setCancellationRequestedAt(Instant.now());
            persisted = proposed;
        }
        transaction.setPaymentStatus(PaymentStatus.REFUND_REQUESTED);
        PaymentTransaction saved = paymentTransactionRepository.saveAndFlush(transaction);
        return new PreparedCancellation(saved != null ? saved : transaction, persisted);
    }

    private void assertPayoutOutcomeIsKnown(PaymentTransaction transaction) {
        if (transaction.getSettlementStatus() == SettlementStatus.REQUESTED) {
            throw new IllegalStateException("지급 결과를 확인하는 중에는 환불을 시작할 수 없습니다.");
        }
        PayoutTransaction payout = payoutTransactionRepository
                .findTopByPaymentTransactionIdOrderByIdDesc(transaction.getId())
                .orElse(null);
        if (payout == null) {
            return;
        }
        if (payout.getStatus() == SettlementStatus.REQUESTED
                || isAmbiguousPayoutFailure(payout.getFailureCode())
                || requiresManualPayoutReconciliation(payout)) {
            throw new IllegalStateException("지급 결과가 확정되지 않아 환불을 시작할 수 없습니다.");
        }
    }

    private boolean isAmbiguousPayoutFailure(String failureCode) {
        return "TOSS_PAYOUT_REQUEST_FAILED".equals(failureCode)
                || "TOSS_PAYOUT_EMPTY_RESPONSE".equals(failureCode)
                || "TOSS_PAYOUT_NO_PROVIDER_REF".equals(failureCode)
                || "PAYOUT_LEGACY_SNAPSHOT_RECONCILIATION_REQUIRED".equals(failureCode)
                || "PAYOUT_MANUAL_RECONCILIATION_REQUIRED".equals(failureCode);
    }

    private boolean requiresManualPayoutReconciliation(PayoutTransaction payout) {
        if (payout == null) {
            return false;
        }
        if (payout.getStatus() == SettlementStatus.REQUESTED) {
            return true;
        }
        if (payout.getStatus() == SettlementStatus.PENDING) {
            return !"SNAPSHOT_V1".equals(payout.getClaimProtocol());
        }
        if (payout.getStatus() == SettlementStatus.COMPLETED) {
            return !"RECOVERY_OFFSET".equals(payout.getProviderRef())
                    && !"PAYOUT_COMPLETION_VERIFIED".equals(payout.getFailureCode());
        }
        if (payout.getStatus() == SettlementStatus.FAILED) {
            boolean hasProviderSnapshot = payout.getProviderCode() != null
                    && !payout.getProviderCode().isBlank();
            if (hasProviderSnapshot) {
                return false;
            }
            return !"SELLER_PROFILE_MISSING".equals(payout.getFailureCode())
                    && !"PAYMENT_PAYOUT_DISABLED".equals(payout.getFailureCode())
                    && !"PAYMENT_NOT_PAYABLE".equals(payout.getFailureCode());
        }
        return false;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentTransaction finalizeReconciledCancellation(
            PaymentTransaction candidate,
            int canceledAmount) {
        PaymentTransaction transaction = paymentTransactionRepository.findByIdForUpdate(candidate.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "결제 트랜잭션을 찾을 수 없습니다: " + candidate.getId()));
        PayoutTransaction payout = payoutTransactionRepository
                .findTopByPaymentTransactionIdForUpdateOrderByIdDesc(transaction.getId())
                .orElse(null);
        boolean requiresManualReconciliation = requiresManualPayoutReconciliation(payout);
        boolean wasSettled = transaction.getSettlementStatus() == SettlementStatus.COMPLETED
                || (payout != null && payout.getStatus() == SettlementStatus.COMPLETED);
        int originalPaidAmount = payout != null && payout.getStatus() == SettlementStatus.COMPLETED
                ? Math.max(0, Objects.requireNonNullElse(payout.getRequestedAmount(), 0)
                        + Objects.requireNonNullElse(payout.getRecoveryOffsetAmount(), 0))
                : Math.max(0, Objects.requireNonNullElse(
                        transaction.getNetAmount(),
                        transaction.getGrossAmount()));
        int grossAmount = Math.max(0, Objects.requireNonNullElse(transaction.getGrossAmount(), 0));
        int actualRefundAmount = Math.min(grossAmount, Math.max(0, canceledAmount));
        int actualFeeAmount = Math.max(0, grossAmount - actualRefundAmount);
        transaction.setRefundAmount(actualRefundAmount);
        transaction.setFeeAmount(actualFeeAmount);
        transaction.setNetAmount(actualFeeAmount);
        transaction.setPaymentStatus(PaymentStatus.CANCELED);
        transaction.setProviderReconciledAt(Instant.now());
        transaction.setSettlementStatus(requiresManualReconciliation
                ? SettlementStatus.REQUESTED
                : wasSettled
                    ? SettlementStatus.REFUNDED_AFTER_SETTLEMENT
                    : SettlementStatus.SKIPPED);
        paymentTransactionRepository.save(transaction);
        if (requiresManualReconciliation && payout != null) {
            payout.setFailureCode("PAYOUT_LEGACY_SNAPSHOT_RECONCILIATION_REQUIRED");
            payout.setFailReason("환불 완료 후 레거시 지급 결과를 운영자가 수동 조정해야 합니다.");
            payout.setNextRetryAt(null);
            payoutTransactionRepository.save(payout);
        } else if (wasSettled) {
            sellerRecoveryService.recordSettledRefund(transaction, originalPaidAmount);
        }
        return transaction;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentTransaction markRefundFailed(PaymentTransaction candidate) {
        PaymentTransaction transaction = paymentTransactionRepository.findByIdForUpdate(candidate.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "결제 트랜잭션을 찾을 수 없습니다: " + candidate.getId()));
        if (transaction.getPaymentStatus() == PaymentStatus.CANCELED) {
            return transaction;
        }
        transaction.setPaymentStatus(PaymentStatus.REFUND_FAILED);
        return paymentTransactionRepository.save(transaction);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentTransaction createFallbackTransaction(PartyApplication application) {
        Party party = partyRepository.findById(application.getPartyId())
                .orElseThrow(() -> new PartyNotFoundException(application.getPartyId()));
        int grossAmount = application.getDepositAmount() != null ? application.getDepositAmount() : 0;
        PaymentTransaction transaction = PaymentTransaction.builder()
                .partyId(application.getPartyId())
                .applicationId(application.getId())
                .buyerUserId(application.getApplicantId())
                .sellerUserId(party.getHostId())
                .flowType(inferFlowType(application.getPaymentType()))
                .orderId(application.getOrderId())
                .paymentKey(application.getPaymentKey())
                .grossAmount(grossAmount)
                .feeAmount(0)
                .refundAmount(0)
                .netAmount(grossAmount)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.PENDING)
                .build();
        return paymentTransactionRepository.saveAndFlush(transaction);
    }

    private CancellationIntent existingOrProposed(
            PaymentTransaction transaction,
            CancellationIntent proposed) {
        CancellationIntent existing = existingIntent(transaction);
        return existing != null ? existing : proposed;
    }

    private CancellationIntent existingIntent(PaymentTransaction transaction) {
        if (transaction.getCancellationRequestedAt() == null
                || transaction.getRequestedRefundAmount() == null
                || transaction.getRequestedFeeAmount() == null
                || transaction.getCancelReasonType() == null
                || transaction.getRefundPolicyApplied() == null) {
            return null;
        }
        return new CancellationIntent(
                transaction.getCancelReasonType(),
                transaction.getCancelMemo(),
                transaction.getRequestedRefundAmount(),
                transaction.getRequestedFeeAmount(),
                transaction.getRefundPolicyApplied(),
                true);
    }

    private PaymentFlowType inferFlowType(PartyApplication.PaymentType paymentType) {
        return paymentType == PartyApplication.PaymentType.FULL
                ? PaymentFlowType.SELLING_FULL
                : PaymentFlowType.DEPOSIT;
    }

    public record CancellationIntent(
            CancelReasonType reasonType,
            String memo,
            int refundAmount,
            int feeAmount,
            String policyApplied,
            boolean existing) {
    }

    public record PreparedCancellation(
            PaymentTransaction transaction,
            CancellationIntent intent) {
    }
}
