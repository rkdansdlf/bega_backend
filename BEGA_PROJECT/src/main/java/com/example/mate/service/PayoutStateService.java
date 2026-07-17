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
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PayoutStateService {

    private static final int MAX_PAYOUT_RETRY_ATTEMPTS = 5;
    private static final long BASE_RETRY_DELAY_SECONDS = 30;
    private static final long MAX_RETRY_DELAY_SECONDS = 3600;

    private final PayoutTransactionRepository payoutTransactionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SellerRecoveryService sellerRecoveryService;
    private final PaymentMetricsService metricsService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PayoutTransaction complete(
            PaymentTransaction paymentCandidate,
            PayoutTransaction payoutCandidate,
            String providerRef) {
        LockedState state = lockState(paymentCandidate, payoutCandidate);
        PaymentTransaction payment = state.payment();
        PayoutTransaction payout = state.payout();
        if (payout.getStatus() == SettlementStatus.COMPLETED
                || payout.getStatus() == SettlementStatus.FAILED
                || payout.getStatus() == SettlementStatus.SKIPPED) {
            return payout;
        }

        if (providerRef != null && !providerRef.isBlank()) {
            payout.setProviderRef(providerRef);
        }
        sellerRecoveryService.applyReservedOffset(payout);
        payout.setCompletedAt(Instant.now());
        payout.setStatus(SettlementStatus.COMPLETED);
        payout.setFailureCode("PAYOUT_COMPLETION_VERIFIED");
        payout.setFailReason(null);
        payout.setNextRetryAt(null);
        payoutTransactionRepository.save(payout);

        if (payment.getPaymentStatus() == PaymentStatus.CANCELED) {
            payment.setSettlementStatus(SettlementStatus.REFUNDED_AFTER_SETTLEMENT);
            paymentTransactionRepository.save(payment);
            int originalPaidAmount = Math.max(
                    0,
                    Objects.requireNonNullElse(payout.getRequestedAmount(), 0)
                            + Objects.requireNonNullElse(payout.getRecoveryOffsetAmount(), 0));
            sellerRecoveryService.recordSettledRefund(payment, originalPaidAmount);
        } else {
            payment.setSettlementStatus(SettlementStatus.COMPLETED);
            paymentTransactionRepository.save(payment);
        }
        metricsService.recordPayout("success");
        return payout;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PayoutTransaction keepRequested(
            PaymentTransaction paymentCandidate,
            PayoutTransaction payoutCandidate,
            String providerRef,
            String failureCode,
            String failReason,
            boolean incrementRetryCount) {
        LockedState state = lockState(paymentCandidate, payoutCandidate);
        PaymentTransaction payment = state.payment();
        PayoutTransaction payout = state.payout();
        if (payout.getStatus() == SettlementStatus.COMPLETED
                || payout.getStatus() == SettlementStatus.FAILED
                || payout.getStatus() == SettlementStatus.SKIPPED) {
            return payout;
        }

        if (providerRef != null && !providerRef.isBlank()) {
            payout.setProviderRef(providerRef);
        }
        payout.setStatus(SettlementStatus.REQUESTED);
        payout.setCompletedAt(null);
        if (incrementRetryCount) {
            payout.setRetryCount(Objects.requireNonNullElse(payout.getRetryCount(), 0) + 1);
        }
        int attempt = Math.max(1, Objects.requireNonNullElse(payout.getRetryCount(), 0));
        if (attempt >= MAX_PAYOUT_RETRY_ATTEMPTS) {
            payout.setFailureCode("PAYOUT_MANUAL_RECONCILIATION_REQUIRED");
            payout.setFailReason("지급 결과를 자동 확인하지 못했습니다. 운영자 수동 조정이 필요합니다.");
            payout.setNextRetryAt(null);
            metricsService.recordPayout("manual_reconciliation");
        } else {
            payout.setFailureCode(failureCode);
            payout.setFailReason(failReason);
            payout.setNextRetryAt(Instant.now().plusSeconds(calculateRetryDelaySeconds(attempt)));
            metricsService.recordPayout("pending");
        }
        payoutTransactionRepository.save(payout);
        payment.setSettlementStatus(SettlementStatus.REQUESTED);
        paymentTransactionRepository.save(payment);
        return payout;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PayoutTransaction fail(
            PaymentTransaction paymentCandidate,
            PayoutTransaction payoutCandidate,
            String providerRef,
            String failureCode,
            String failReason,
            boolean incrementRetryCount,
            boolean retryable) {
        LockedState state = lockState(paymentCandidate, payoutCandidate);
        PaymentTransaction payment = state.payment();
        PayoutTransaction payout = state.payout();
        if (payout.getStatus() == SettlementStatus.COMPLETED
                || payout.getStatus() == SettlementStatus.FAILED
                || payout.getStatus() == SettlementStatus.SKIPPED) {
            return payout;
        }

        if (providerRef != null && !providerRef.isBlank()) {
            payout.setProviderRef(providerRef);
        }
        sellerRecoveryService.releaseReservedOffset(payout);
        payout.setRecoveryOffsetAmount(0);
        payout.setRecoveryOffsetReservedAt(null);
        payout.setStatus(SettlementStatus.FAILED);
        if (incrementRetryCount) {
            payout.setRetryCount(Objects.requireNonNullElse(payout.getRetryCount(), 0) + 1);
        }
        payout.setFailureCode(failureCode);
        payout.setFailReason(failReason);
        int attempt = Math.max(1, Objects.requireNonNullElse(payout.getRetryCount(), 0));
        payout.setNextRetryAt(retryable && attempt < MAX_PAYOUT_RETRY_ATTEMPTS
                ? Instant.now().plusSeconds(calculateRetryDelaySeconds(attempt))
                : null);
        payoutTransactionRepository.save(payout);

        payment.setSettlementStatus(SettlementStatus.FAILED);
        paymentTransactionRepository.save(payment);
        metricsService.recordPayout("fail");
        return payout;
    }

    private LockedState lockState(
            PaymentTransaction paymentCandidate,
            PayoutTransaction payoutCandidate) {
        PaymentTransaction payment = paymentTransactionRepository
                .findByIdForUpdate(paymentCandidate.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "결제 트랜잭션을 찾을 수 없습니다: " + paymentCandidate.getId()));
        PayoutTransaction payout = payoutTransactionRepository
                .findByIdForUpdate(payoutCandidate.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "지급 트랜잭션을 찾을 수 없습니다: " + payoutCandidate.getId()));
        return new LockedState(payment, payout);
    }

    private long calculateRetryDelaySeconds(int retryCount) {
        long delay = BASE_RETRY_DELAY_SECONDS * (1L << Math.max(0, retryCount - 1));
        return Math.min(MAX_RETRY_DELAY_SECONDS, delay);
    }

    private record LockedState(
            PaymentTransaction payment,
            PayoutTransaction payout) {
    }
}
