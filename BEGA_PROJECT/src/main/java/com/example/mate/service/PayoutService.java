package com.example.mate.service;

import com.example.mate.entity.PayoutTransaction;
import com.example.mate.entity.PaymentTransaction;
import com.example.mate.entity.PaymentStatus;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.service.payout.PayoutGateway;
import com.example.mate.service.payout.PayoutGateway.PayoutGatewayException;
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
    private final SellerPayoutProfileService sellerPayoutProfileService;
    private final SellerRecoveryService sellerRecoveryService;
    private final JobScheduler jobScheduler;

    private final Map<String, PayoutGateway> payoutGateways;

    @Autowired
    public PayoutService(
            PayoutTransactionRepository payoutTransactionRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            PaymentMetricsService metricsService,
            SellerPayoutProfileService sellerPayoutProfileService,
            SellerRecoveryService sellerRecoveryService,
            JobScheduler jobScheduler,
            java.util.List<PayoutGateway> payoutGateways) {
        this.payoutTransactionRepository = payoutTransactionRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.metricsService = metricsService;
        this.sellerPayoutProfileService = sellerPayoutProfileService;
        this.sellerRecoveryService = sellerRecoveryService;
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

    @Transactional(noRollbackFor = RuntimeException.class)
    public PayoutTransaction requestPayout(PaymentTransaction paymentTransaction) {
        if (paymentTransaction == null || paymentTransaction.getId() == null) {
            throw new IllegalArgumentException("결제 트랜잭션이 올바르지 않습니다.");
        }

        PaymentTransaction lockedPayment = paymentTransactionRepository
                .findByIdForUpdate(paymentTransaction.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "결제 트랜잭션을 찾을 수 없습니다: " + paymentTransaction.getId()));

        PayoutTransaction payout = payoutTransactionRepository
                .findTopByPaymentTransactionIdForUpdateOrderByIdDesc(lockedPayment.getId())
                .orElseGet(() -> PayoutTransaction.builder()
                        .paymentTransactionId(lockedPayment.getId())
                        .sellerId(lockedPayment.getSellerUserId())
                        .requestedAmount(lockedPayment.getNetAmount())
                        .status(SettlementStatus.PENDING)
                        .build());

        if (payout.getStatus() == SettlementStatus.COMPLETED
                || payout.getStatus() == SettlementStatus.REQUESTED) {
            return payout;
        }

        if (payout.getStatus() == SettlementStatus.SKIPPED) {
            return payout;
        }

        if (!isPayable(lockedPayment)) {
            return markPayoutSkipped(
                    lockedPayment,
                    payout,
                    "PAYMENT_NOT_PAYABLE",
                    "paymentStatus=" + lockedPayment.getPaymentStatus(),
                    false);
        }

        if (payout.getStatus() == SettlementStatus.FAILED
                && payout.getRetryCount() != null
                && payout.getRetryCount() >= MAX_PAYOUT_RETRY_ATTEMPTS) {
            return payout;
        }

        if (!payoutEnabled) {
            return markPayoutSkipped(
                    lockedPayment,
                    payout,
                    "PAYMENT_PAYOUT_DISABLED",
                    "payment.payout.enabled=false");
        }

        return executePayoutRequest(lockedPayment, payout);
    }

    @Job(name = "Retry Payout")
    @Transactional
    public void retryPayout(Long payoutId) {
        payoutTransactionRepository.findById(payoutId).ifPresent(candidate -> {
            PaymentTransaction paymentTransaction = paymentTransactionRepository
                    .findByIdForUpdate(candidate.getPaymentTransactionId())
                    .orElse(null);
            if (paymentTransaction == null) {
                return;
            }
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

            if (!isPayable(paymentTransaction)) {
                markPayoutSkipped(
                        paymentTransaction,
                        payout,
                        "PAYMENT_NOT_PAYABLE",
                        "paymentStatus=" + paymentTransaction.getPaymentStatus(),
                        false);
                return;
            }
            try {
                executePayoutRequest(paymentTransaction, payout);
            } catch (RuntimeException e) {
                log.error("[Payout] 재시도 지급 처리 실패: payoutId={}", payoutId, e);
            }
            });
        });
    }

    private PayoutTransaction executePayoutRequest(PaymentTransaction paymentTransaction, PayoutTransaction payout) {
        if (!isPayable(paymentTransaction)) {
            return markPayoutSkipped(
                    paymentTransaction,
                    payout,
                    "PAYMENT_NOT_PAYABLE",
                    "paymentStatus=" + paymentTransaction.getPaymentStatus(),
                    false);
        }
        if (!payoutEnabled) {
            return markPayoutSkipped(
                    paymentTransaction,
                    payout,
                    "PAYMENT_PAYOUT_DISABLED",
                    "payment.payout.enabled=false");
        }

        String providerCode = resolveProviderCode();
        int payoutAmount = reserveRecoveryOffset(paymentTransaction, payout);
        if (payoutAmount == 0) {
            return completeWithRecoveryOffset(paymentTransaction, payout);
        }

        payout.setStatus(SettlementStatus.REQUESTED);
        payout.setRequestedAmount(payoutAmount);
        payout.setRequestedAt(Instant.now());
        payout.setFailReason(null);
        payout.setFailureCode(null);
        payout.setLastRetryAt(Instant.now());
        payout.setNextRetryAt(null);
        PayoutTransaction claimed = payoutTransactionRepository.saveAndFlush(payout);
        if (claimed != null) {
            payout = claimed;
        }

        try {
            PayoutGateway.PayoutRequest payoutRequest = buildPayoutRequest(
                    paymentTransaction,
                    providerCode,
                    payoutAmount);
            PayoutGateway payoutGateway = resolveGateway(providerCode);
            PayoutGateway.PayoutResult gatewayResult = payoutGateway.requestPayout(payoutRequest);
            payout.setProviderRef(gatewayResult.providerRef());
            payout.setCompletedAt(Instant.now());
            payout.setStatus(SettlementStatus.COMPLETED);
            payout = payoutTransactionRepository.save(payout);

            paymentTransaction.setSettlementStatus(SettlementStatus.COMPLETED);
            paymentTransactionRepository.save(paymentTransaction);
            log.info("[Payout] payout completed: paymentTransactionId={}, payoutId={}",
                    paymentTransaction.getId(), payout.getId());
            metricsService.recordPayout("success");
        } catch (RuntimeException e) {
            String failureCode = resolveFailureCode(e);
            payout.setStatus(SettlementStatus.FAILED);
            payout.setRetryCount(payout.getRetryCount() == null ? 1 : payout.getRetryCount() + 1);
            payout.setFailureCode(failureCode);
            payout.setFailReason(String.valueOf(e.getMessage()));
            long nextDelaySeconds = calculateRetryDelaySeconds(payout.getRetryCount());
            payout.setNextRetryAt(Instant.now().plusSeconds(nextDelaySeconds));
            payoutTransactionRepository.save(payout);

            paymentTransaction.setSettlementStatus(SettlementStatus.FAILED);
            paymentTransactionRepository.save(paymentTransaction);
            metricsService.recordPayout("fail");

            if (isRetryableFailure(failureCode)
                    && payout.getRetryCount() < MAX_PAYOUT_RETRY_ATTEMPTS) {
                scheduleRetry(payout.getId(), payout.getNextRetryAt());
            }

            log.error("[Payout] payout failed: paymentTransactionId={}, payoutId={}",
                    paymentTransaction.getId(), payout.getId(), e);
            throw e;
        }

        return payout;
    }

    private int reserveRecoveryOffset(
            PaymentTransaction paymentTransaction,
            PayoutTransaction payout) {
        int netAmount = Math.max(0, Objects.requireNonNullElse(paymentTransaction.getNetAmount(), 0));
        if (payout.getRecoveryOffsetReservedAt() == null) {
            SellerRecoveryService.RecoveryOffsetResult result = sellerRecoveryService.reserveOffset(
                    paymentTransaction.getSellerUserId(),
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

    private PayoutTransaction completeWithRecoveryOffset(
            PaymentTransaction paymentTransaction,
            PayoutTransaction payout) {
        Instant now = Instant.now();
        payout.setStatus(SettlementStatus.COMPLETED);
        payout.setRequestedAmount(0);
        payout.setProviderRef("RECOVERY_OFFSET");
        payout.setRequestedAt(now);
        payout.setCompletedAt(now);
        payout.setFailReason(null);
        payout.setFailureCode(null);
        payout.setNextRetryAt(null);
        payout = payoutTransactionRepository.save(payout);

        paymentTransaction.setSettlementStatus(SettlementStatus.COMPLETED);
        paymentTransactionRepository.save(paymentTransaction);
        metricsService.recordPayout("recovery_offset");
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
            String failureCode,
            String failReason) {
        return markPayoutSkipped(paymentTransaction, payout, failureCode, failReason, true);
    }

    private PayoutTransaction markPayoutSkipped(
            PaymentTransaction paymentTransaction,
            PayoutTransaction payout,
            String failureCode,
            String failReason,
            boolean updatePaymentSettlement) {
        if (payout.getStatus() == SettlementStatus.SKIPPED) {
            return payout;
        }

        payout.setStatus(SettlementStatus.SKIPPED);
        payout.setRequestedAmount(paymentTransaction.getNetAmount());
        payout.setRequestedAt(Instant.now());
        payout.setLastRetryAt(null);
        payout.setNextRetryAt(null);
        payout.setFailureCode(failureCode);
        payout.setFailReason(failReason);
        payout = payoutTransactionRepository.save(payout);

        if (updatePaymentSettlement) {
            paymentTransaction.setSettlementStatus(SettlementStatus.SKIPPED);
            paymentTransactionRepository.save(paymentTransaction);
        }
        metricsService.recordPayout("skip");

        return payout;
    }

    private boolean isPayable(PaymentTransaction paymentTransaction) {
        return paymentTransaction.getPaymentStatus() == PaymentStatus.PAID;
    }

    private PayoutGateway resolveGateway(String providerCode) {
        PayoutGateway gateway = payoutGateways.get(providerCode);
        if (gateway == null) {
            throw new IllegalStateException("지원되지 않는 지급대행 provider: " + payoutProvider);
        }
        return gateway;

    }

    private PayoutGateway.PayoutRequest buildPayoutRequest(
            PaymentTransaction paymentTransaction,
            String providerCode,
            int payoutAmount) {
        String providerSellerId = null;
        if ("TOSS".equals(providerCode)) {
            providerSellerId = sellerPayoutProfileService.getRequiredProviderSellerId(
                    paymentTransaction.getSellerUserId(),
                    providerCode);
        }

        return new PayoutGateway.PayoutRequest(
                paymentTransaction.getId(),
                paymentTransaction.getOrderId(),
                paymentTransaction.getSellerUserId(),
                providerSellerId,
                payoutAmount,
                "KRW",
                "mate-payout-" + paymentTransaction.getId());
    }

    private String resolveProviderCode() {
        return Objects.toString(payoutProvider, "SIM").toUpperCase(Locale.ROOT);
    }

    private String resolveFailureCode(RuntimeException e) {
        if (e instanceof PayoutGatewayException payoutGatewayException
                && payoutGatewayException.getFailureCode() != null
                && !payoutGatewayException.getFailureCode().isBlank()) {
            return payoutGatewayException.getFailureCode();
        }
        if (e.getMessage() != null && e.getMessage().contains("SELLER_PROFILE_MISSING")) {
            return "SELLER_PROFILE_MISSING";
        }
        return e.getClass().getSimpleName();
    }

    private boolean isRetryableFailure(String failureCode) {
        if (failureCode == null) {
            return true;
        }
        return !"SELLER_PROFILE_MISSING".equals(failureCode)
                && !"PAYMENT_PAYOUT_DISABLED".equals(failureCode);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<PayoutTransaction> findLatestByPaymentTransactionId(Long paymentTransactionId) {
        if (paymentTransactionId == null) {
            return java.util.Optional.empty();
        }
        return payoutTransactionRepository.findTopByPaymentTransactionIdOrderByIdDesc(paymentTransactionId);
    }

}
