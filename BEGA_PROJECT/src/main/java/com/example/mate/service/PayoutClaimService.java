package com.example.mate.service;

import com.example.mate.entity.PaymentStatus;
import com.example.mate.entity.PaymentTransaction;
import com.example.mate.entity.PayoutTransaction;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.repository.PaymentTransactionRepository;
import com.example.mate.repository.PayoutTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PayoutClaimService {

    private static final int MAX_PAYOUT_RETRY_ATTEMPTS = 5;
    private static final long PROVIDER_CALL_LEASE_SECONDS = 180;
    private static final String SNAPSHOT_CLAIM_PROTOCOL = "SNAPSHOT_V1";

    private final PayoutTransactionRepository payoutTransactionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SellerRecoveryService sellerRecoveryService;
    private final PaymentMetricsService metricsService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClaimedPayout claimInitial(Long paymentTransactionId, boolean payoutEnabled) {
        PaymentTransaction payment = lockPayment(paymentTransactionId);
        PayoutTransaction payout = payoutTransactionRepository
                .findTopByPaymentTransactionIdForUpdateOrderByIdDesc(payment.getId())
                .orElseGet(() -> PayoutTransaction.builder()
                        .paymentTransactionId(payment.getId())
                        .sellerId(payment.getSellerUserId())
                        .requestedAmount(payment.getNetAmount())
                        .status(SettlementStatus.PENDING)
                        .build());

        if (payout.getStatus() == SettlementStatus.COMPLETED
                || payout.getStatus() == SettlementStatus.REQUESTED
                || payout.getStatus() == SettlementStatus.FAILED
                || payout.getStatus() == SettlementStatus.SKIPPED) {
            return new ClaimedPayout(payment, payout, ClaimAction.NONE);
        }
        if (payment.getPaymentStatus() != PaymentStatus.PAID) {
            markSkipped(payment, payout, "PAYMENT_NOT_PAYABLE", false);
            return new ClaimedPayout(payment, payout, ClaimAction.NONE);
        }
        if (!payoutEnabled) {
            markSkipped(payment, payout, "PAYMENT_PAYOUT_DISABLED", true);
            return new ClaimedPayout(payment, payout, ClaimAction.NONE);
        }

        if (payout.getId() == null) {
            payout = payoutTransactionRepository.saveAndFlush(payout);
        }
        int payoutAmount = reserveRecoveryOffset(payment, payout);
        if (payoutAmount == 0) {
            completeWithRecoveryOffset(payment, payout);
            return new ClaimedPayout(payment, payout, ClaimAction.NONE);
        }
        persistDurableRequestClaim(payment, payout, payoutAmount);
        return new ClaimedPayout(payment, payout, ClaimAction.CALL_PROVIDER);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClaimedPayout claimRetry(Long payoutId, boolean payoutEnabled) {
        PayoutTransaction candidate = payoutTransactionRepository.findById(payoutId).orElse(null);
        if (candidate == null) {
            return ClaimedPayout.none();
        }
        PaymentTransaction payment = lockPayment(candidate.getPaymentTransactionId());
        PayoutTransaction payout = payoutTransactionRepository.findByIdForUpdate(payoutId).orElse(null);
        if (payout == null || payout.getStatus() != SettlementStatus.REQUESTED) {
            return ClaimedPayout.none();
        }
        if (!SNAPSHOT_CLAIM_PROTOCOL.equals(payout.getClaimProtocol())) {
            payout.setFailureCode("PAYOUT_LEGACY_SNAPSHOT_RECONCILIATION_REQUIRED");
            payout.setFailReason("provider snapshot이 없는 레거시 지급 건은 운영자 수동 조정이 필요합니다.");
            payout.setNextRetryAt(null);
            payoutTransactionRepository.saveAndFlush(payout);
            payment.setSettlementStatus(SettlementStatus.REQUESTED);
            paymentTransactionRepository.save(payment);
            metricsService.recordPayout("manual_reconciliation");
            return new ClaimedPayout(payment, payout, ClaimAction.NONE);
        }
        if (Objects.requireNonNullElse(payout.getRetryCount(), 0) >= MAX_PAYOUT_RETRY_ATTEMPTS) {
            payout.setStatus(SettlementStatus.REQUESTED);
            payout.setFailureCode("PAYOUT_MANUAL_RECONCILIATION_REQUIRED");
            payout.setFailReason("지급 결과를 자동 확인하지 못했습니다. 운영자 수동 조정이 필요합니다.");
            payout.setNextRetryAt(null);
            payoutTransactionRepository.save(payout);
            payment.setSettlementStatus(SettlementStatus.REQUESTED);
            paymentTransactionRepository.save(payment);
            metricsService.recordPayout("manual_reconciliation");
            return new ClaimedPayout(payment, payout, ClaimAction.NONE);
        }
        if (!payoutEnabled) {
            return new ClaimedPayout(payment, payout, ClaimAction.NONE);
        }
        if (payout.getNextRetryAt() != null && payout.getNextRetryAt().isAfter(Instant.now())) {
            return new ClaimedPayout(payment, payout, ClaimAction.NONE);
        }
        if (payout.getStatus() == SettlementStatus.REQUESTED
                && payout.getProviderRef() != null
                && !payout.getProviderRef().isBlank()) {
            Instant now = Instant.now();
            payout.setLastRetryAt(now);
            payout.setNextRetryAt(now.plusSeconds(PROVIDER_CALL_LEASE_SECONDS));
            payoutTransactionRepository.saveAndFlush(payout);
            return new ClaimedPayout(payment, payout, ClaimAction.POLL_PROVIDER);
        }
        if (payment.getPaymentStatus() != PaymentStatus.PAID) {
            return new ClaimedPayout(payment, payout, ClaimAction.NONE);
        }

        int payoutAmount = reserveRecoveryOffset(payment, payout);
        if (payoutAmount == 0) {
            completeWithRecoveryOffset(payment, payout);
            return new ClaimedPayout(payment, payout, ClaimAction.NONE);
        }
        persistDurableRequestClaim(payment, payout, payoutAmount);
        return new ClaimedPayout(payment, payout, ClaimAction.CALL_PROVIDER);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PayoutTransaction bindProviderPayload(
            Long paymentTransactionId,
            Long payoutId,
            String providerCode,
            String providerSellerId) {
        lockPayment(paymentTransactionId);
        PayoutTransaction payout = payoutTransactionRepository.findByIdForUpdate(payoutId)
                .orElseThrow(() -> new IllegalStateException(
                        "지급 트랜잭션을 찾을 수 없습니다: " + payoutId));
        String normalizedProviderCode = Objects.toString(providerCode, "")
                .trim()
                .toUpperCase(Locale.ROOT);
        if (normalizedProviderCode.isBlank()) {
            throw new IllegalArgumentException("지급대행 provider가 올바르지 않습니다.");
        }
        if ("TOSS".equals(normalizedProviderCode)
                && (providerSellerId == null || providerSellerId.isBlank())) {
            throw new IllegalArgumentException("TOSS 판매자 지급 식별자가 올바르지 않습니다.");
        }
        if (payout.getProviderCode() != null
                && !payout.getProviderCode().isBlank()
                && !payout.getProviderCode().equals(normalizedProviderCode)) {
            throw new IllegalStateException("이미 고정된 지급대행 provider를 변경할 수 없습니다.");
        }
        if (payout.getProviderSellerId() != null
                && !payout.getProviderSellerId().isBlank()
                && !Objects.equals(payout.getProviderSellerId(), providerSellerId)) {
            throw new IllegalStateException("이미 고정된 판매자 지급 식별자를 변경할 수 없습니다.");
        }
        payout.setProviderCode(normalizedProviderCode);
        if (payout.getProviderSellerId() == null || payout.getProviderSellerId().isBlank()) {
            payout.setProviderSellerId(providerSellerId);
        }
        return payoutTransactionRepository.saveAndFlush(payout);
    }

    private PaymentTransaction lockPayment(Long paymentTransactionId) {
        return paymentTransactionRepository.findByIdForUpdate(paymentTransactionId)
                .orElseThrow(() -> new IllegalStateException(
                        "결제 트랜잭션을 찾을 수 없습니다: " + paymentTransactionId));
    }

    private int reserveRecoveryOffset(PaymentTransaction payment, PayoutTransaction payout) {
        int netAmount = Math.max(0, Objects.requireNonNullElse(payment.getNetAmount(), 0));
        if (payout.getRecoveryOffsetReservedAt() == null) {
            SellerRecoveryService.RecoveryOffsetResult result = sellerRecoveryService.reserveOffset(
                    payout,
                    payment.getSellerUserId(),
                    netAmount);
            int offsetAmount = result != null ? result.offsetAmount() : 0;
            payout.setRecoveryOffsetAmount(Math.min(netAmount, Math.max(0, offsetAmount)));
            payout.setRecoveryOffsetReservedAt(Instant.now());
            payoutTransactionRepository.save(payout);
        }
        int reservedOffset = Math.min(
                netAmount,
                Math.max(0, Objects.requireNonNullElse(payout.getRecoveryOffsetAmount(), 0)));
        return netAmount - reservedOffset;
    }

    private void persistDurableRequestClaim(
            PaymentTransaction payment,
            PayoutTransaction payout,
            int payoutAmount) {
        Instant now = Instant.now();
        payout.setStatus(SettlementStatus.REQUESTED);
        payout.setRequestedAmount(payoutAmount);
        if (payout.getRequestedAt() == null) {
            payout.setRequestedAt(now);
        }
        payout.setCompletedAt(null);
        payout.setFailReason(null);
        payout.setFailureCode(null);
        payout.setClaimProtocol(SNAPSHOT_CLAIM_PROTOCOL);
        payout.setLastRetryAt(now);
        payout.setNextRetryAt(now.plusSeconds(PROVIDER_CALL_LEASE_SECONDS));
        payoutTransactionRepository.saveAndFlush(payout);

        payment.setSettlementStatus(SettlementStatus.REQUESTED);
        paymentTransactionRepository.saveAndFlush(payment);
    }

    private void completeWithRecoveryOffset(PaymentTransaction payment, PayoutTransaction payout) {
        Instant now = Instant.now();
        sellerRecoveryService.applyReservedOffset(payout);
        payout.setStatus(SettlementStatus.COMPLETED);
        payout.setRequestedAmount(0);
        payout.setProviderRef("RECOVERY_OFFSET");
        payout.setClaimProtocol(SNAPSHOT_CLAIM_PROTOCOL);
        payout.setRequestedAt(now);
        payout.setCompletedAt(now);
        payout.setFailReason(null);
        payout.setFailureCode("PAYOUT_COMPLETION_VERIFIED");
        payout.setNextRetryAt(null);
        payoutTransactionRepository.save(payout);

        payment.setSettlementStatus(SettlementStatus.COMPLETED);
        paymentTransactionRepository.save(payment);
        metricsService.recordPayout("recovery_offset");
    }

    private void markSkipped(
            PaymentTransaction payment,
            PayoutTransaction payout,
            String failureCode,
            boolean updatePaymentSettlement) {
        sellerRecoveryService.releaseReservedOffset(payout);
        payout.setRecoveryOffsetAmount(0);
        payout.setRecoveryOffsetReservedAt(null);
        payout.setStatus(SettlementStatus.SKIPPED);
        payout.setRequestedAmount(payment.getNetAmount());
        payout.setRequestedAt(Instant.now());
        payout.setLastRetryAt(null);
        payout.setNextRetryAt(null);
        payout.setFailureCode(failureCode);
        payout.setFailReason("paymentStatus=" + payment.getPaymentStatus());
        payoutTransactionRepository.save(payout);
        if (updatePaymentSettlement) {
            payment.setSettlementStatus(SettlementStatus.SKIPPED);
            paymentTransactionRepository.save(payment);
        }
        metricsService.recordPayout("skip");
    }

    public enum ClaimAction {
        NONE,
        CALL_PROVIDER,
        POLL_PROVIDER
    }

    public record ClaimedPayout(
            PaymentTransaction payment,
            PayoutTransaction payout,
            ClaimAction action) {

        static ClaimedPayout none() {
            return new ClaimedPayout(null, null, ClaimAction.NONE);
        }
    }
}
