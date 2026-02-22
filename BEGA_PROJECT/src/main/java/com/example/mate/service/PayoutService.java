package com.example.mate.service;

import com.example.mate.entity.PayoutTransaction;
import com.example.mate.entity.PaymentTransaction;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.service.payout.PayoutGateway;
import com.example.mate.repository.PayoutTransactionRepository;
import com.example.mate.repository.PaymentTransactionRepository;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutService {

    private static final int MAX_PAYOUT_RETRY_ATTEMPTS = 5;
    private static final long BASE_RETRY_DELAY_SECONDS = 30;
    private static final long MAX_RETRY_DELAY_SECONDS = 3600;

    private final PayoutTransactionRepository payoutTransactionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentMetricsService metricsService;
    private final JobScheduler jobScheduler;

    private final Map<String, PayoutGateway> payoutGateways;

    @Autowired
    public PayoutService(
            PayoutTransactionRepository payoutTransactionRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            PaymentMetricsService metricsService,
            JobScheduler jobScheduler,
            java.util.List<PayoutGateway> payoutGateways) {
        this.payoutTransactionRepository = payoutTransactionRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.metricsService = metricsService;
        this.jobScheduler = jobScheduler;
        this.payoutGateways = payoutGateways.stream()
                .collect(Collectors.toMap(
                        gateway -> gateway.getProviderCode().toUpperCase(Locale.ROOT),
                        gateway -> gateway,
                        (existing, ignored) -> existing));
    }

    @Value("${payment.payout.enabled:false}")
    private boolean payoutEnabled;

    @Value("${payment.payout.provider:SIM}")
    private String payoutProvider;

    @Transactional
    public PayoutTransaction requestPayout(PaymentTransaction paymentTransaction) {
        if (paymentTransaction == null || paymentTransaction.getId() == null) {
            throw new IllegalArgumentException("결제 트랜잭션이 올바르지 않습니다.");
        }

        PayoutTransaction payout = payoutTransactionRepository
                .findTopByPaymentTransactionIdForUpdateOrderByIdDesc(paymentTransaction.getId())
                .orElseGet(() -> PayoutTransaction.builder()
                        .paymentTransactionId(paymentTransaction.getId())
                        .sellerId(paymentTransaction.getSellerUserId())
                        .requestedAmount(paymentTransaction.getNetAmount())
                        .status(SettlementStatus.PENDING)
                        .build());

        if (payout.getStatus() == SettlementStatus.COMPLETED
                || payout.getStatus() == SettlementStatus.REQUESTED) {
            return payout;
        }

        if (payout.getStatus() == SettlementStatus.SKIPPED) {
            return payout;
        }

        if (payout.getStatus() == SettlementStatus.FAILED
                && payout.getRetryCount() != null
                && payout.getRetryCount() >= MAX_PAYOUT_RETRY_ATTEMPTS) {
            return payout;
        }

        if (!payoutEnabled) {
            return markPayoutSkipped(paymentTransaction, payout, "PAYMENT_PAYOUT_DISABLED");
        }

        return executePayoutRequest(paymentTransaction, payout);
    }

    @Job(name = "Retry Payout")
    @Transactional
    public void retryPayout(Long payoutId) {
        payoutTransactionRepository.findByIdForUpdate(payoutId).ifPresent(payout -> {
            if (payout.getStatus() != SettlementStatus.FAILED) {
                return;
            }

            if (payout.getRetryCount() != null
                    && payout.getRetryCount() >= MAX_PAYOUT_RETRY_ATTEMPTS) {
                return;
            }

            if (!payoutEnabled) {
                return;
            }

            if (payout.getNextRetryAt() != null && payout.getNextRetryAt().isAfter(Instant.now())) {
                return;
            }

            paymentTransactionRepository.findById(payout.getPaymentTransactionId()).ifPresent(paymentTransaction -> {
                try {
                    executePayoutRequest(paymentTransaction, payout);
                } catch (RuntimeException e) {
                    log.error("[Payout] 재시도 지급 처리 실패: payoutId={}", payoutId, e);
                }
            });
        });
    }

    private PayoutTransaction executePayoutRequest(PaymentTransaction paymentTransaction, PayoutTransaction payout) {
        if (!payoutEnabled) {
            return markPayoutSkipped(paymentTransaction, payout, "PAYMENT_PAYOUT_DISABLED");
        }

        payout.setStatus(SettlementStatus.REQUESTED);
        payout.setRequestedAmount(paymentTransaction.getNetAmount());
        payout.setRequestedAt(Instant.now());
        payout.setFailReason(null);
        payout.setLastRetryAt(Instant.now());
        payout.setNextRetryAt(null);
        payout = payoutTransactionRepository.save(payout);

        try {
            String providerRef = resolveGateway().requestPayout(paymentTransaction);
            payout.setProviderRef(providerRef);
            payout.setCompletedAt(Instant.now());
            payout.setStatus(SettlementStatus.COMPLETED);
            payout = payoutTransactionRepository.save(payout);

            paymentTransaction.setSettlementStatus(SettlementStatus.COMPLETED);
            paymentTransactionRepository.save(paymentTransaction);
            log.info("[Payout] payout completed: paymentTransactionId={}, payoutId={}",
                    paymentTransaction.getId(), payout.getId());
            metricsService.recordPayout("success");
        } catch (RuntimeException e) {
            payout.setStatus(SettlementStatus.FAILED);
            payout.setRetryCount(payout.getRetryCount() == null ? 1 : payout.getRetryCount() + 1);
            payout.setFailReason(String.valueOf(e.getMessage()));
            long nextDelaySeconds = calculateRetryDelaySeconds(payout.getRetryCount());
            payout.setNextRetryAt(Instant.now().plusSeconds(nextDelaySeconds));
            payoutTransactionRepository.save(payout);

            paymentTransaction.setSettlementStatus(SettlementStatus.FAILED);
            paymentTransactionRepository.save(paymentTransaction);
            metricsService.recordPayout("fail");

            if (payout.getRetryCount() < MAX_PAYOUT_RETRY_ATTEMPTS) {
                scheduleRetry(payout.getId(), payout.getNextRetryAt());
            }

            log.error("[Payout] payout failed: paymentTransactionId={}, payoutId={}",
                    paymentTransaction.getId(), payout.getId(), e);
            throw e;
        }

        return payout;
    }

    private long calculateRetryDelaySeconds(int retryCount) {
        long delay = BASE_RETRY_DELAY_SECONDS * (1L << Math.max(0, retryCount - 1));
        return Math.min(MAX_RETRY_DELAY_SECONDS, delay);
    }

    private void scheduleRetry(Long payoutId, Instant retryAt) {
        if (jobScheduler == null) {
            return;
        }

        if (retryAt == null || retryAt.isBefore(Instant.now())) {
            retryAt = Instant.now();
        }

        jobScheduler.schedule(
                retryAt,
                () -> retryPayout(payoutId));
    }

    private PayoutTransaction markPayoutSkipped(
            PaymentTransaction paymentTransaction,
            PayoutTransaction payout,
            String failReason) {
        if (payout.getStatus() == SettlementStatus.SKIPPED) {
            return payout;
        }

        payout.setStatus(SettlementStatus.SKIPPED);
        payout.setRequestedAmount(paymentTransaction.getNetAmount());
        payout.setRequestedAt(Instant.now());
        payout.setLastRetryAt(null);
        payout.setNextRetryAt(null);
        payout.setFailReason(failReason);
        payout = payoutTransactionRepository.save(payout);

        paymentTransaction.setSettlementStatus(SettlementStatus.SKIPPED);
        paymentTransactionRepository.save(paymentTransaction);
        metricsService.recordPayout("skip");

        return payout;
    }

    private PayoutGateway resolveGateway() {
        PayoutGateway gateway = payoutGateways.get(Objects.toString(payoutProvider, "SIM").toUpperCase(Locale.ROOT));
        if (gateway == null) {
            throw new IllegalStateException("지원되지 않는 지급대행 provider: " + payoutProvider);
        }
        return gateway;

    }

}
