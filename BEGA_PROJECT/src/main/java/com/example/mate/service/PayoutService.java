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
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
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

    private final PayoutTransactionRepository payoutTransactionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SellerPayoutProfileService sellerPayoutProfileService;
    private final PayoutClaimService payoutClaimService;
    private final PayoutStateService payoutStateService;
    private final JobScheduler jobScheduler;

    private final Map<String, PayoutGateway> payoutGateways;

    @Autowired
    public PayoutService(
            PayoutTransactionRepository payoutTransactionRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            SellerPayoutProfileService sellerPayoutProfileService,
            PayoutClaimService payoutClaimService,
            PayoutStateService payoutStateService,
            JobScheduler jobScheduler,
            java.util.List<PayoutGateway> payoutGateways) {
        this.payoutTransactionRepository = payoutTransactionRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.sellerPayoutProfileService = sellerPayoutProfileService;
        this.payoutClaimService = payoutClaimService;
        this.payoutStateService = payoutStateService;
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

    public PayoutTransaction requestPayout(PaymentTransaction paymentTransaction) {
        if (paymentTransaction == null || paymentTransaction.getId() == null) {
            throw new IllegalArgumentException("결제 트랜잭션이 올바르지 않습니다.");
        }
        PayoutClaimService.ClaimedPayout claim = payoutClaimService.claimInitial(
                paymentTransaction.getId(),
                payoutEnabled);
        if (claim.payout() == null || claim.action() == PayoutClaimService.ClaimAction.NONE) {
            return claim.payout();
        }
        return executePayoutRequest(claim.payment(), claim.payout());
    }

    @Job(name = "Retry Payout")
    public void retryPayout(Long payoutId) {
        PayoutClaimService.ClaimedPayout claim = payoutClaimService.claimRetry(payoutId, payoutEnabled);
        if (claim.payout() == null || claim.action() == PayoutClaimService.ClaimAction.NONE) {
            return;
        }
        if (claim.action() == PayoutClaimService.ClaimAction.POLL_PROVIDER) {
            reconcileRequestedPayout(claim.payment(), claim.payout());
            return;
        }
        try {
            executePayoutRequest(claim.payment(), claim.payout());
        } catch (RuntimeException e) {
            log.error("[Payout] 재시도 지급 처리 실패: payoutId={}", payoutId, e);
        }
    }

    @Scheduled(fixedDelayString = "${payment.payout.reconciliation-sweep-ms:60000}")
    public void reconcileDuePayouts() {
        payoutTransactionRepository
                .findTop100ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                        java.util.List.of(SettlementStatus.REQUESTED),
                        Instant.now())
                .forEach(payout -> {
                    try {
                        retryPayout(payout.getId());
                    } catch (RuntimeException e) {
                        log.error("[Payout] due payout reconciliation failed: payoutId={}", payout.getId(), e);
                    }
                });
    }

    @Scheduled(fixedDelayString = "${payment.payout.missing-claim-sweep-ms:60000}")
    public void recoverMissingPayoutClaims() {
        paymentTransactionRepository.findApprovedWithoutPayout(
                        PaymentStatus.PAID,
                        SettlementStatus.PENDING,
                        PageRequest.of(0, 100))
                .forEach(payment -> {
                    try {
                        requestPayout(payment);
                    } catch (RuntimeException e) {
                        log.error("[Payout] missing durable claim recovery failed: paymentTransactionId={}",
                                payment.getId(), e);
                    }
                });
    }

    private PayoutTransaction executePayoutRequest(PaymentTransaction paymentTransaction, PayoutTransaction payout) {
        int payoutAmount = Math.max(0, Objects.requireNonNullElse(payout.getRequestedAmount(), 0));
        PayoutGateway.PayoutResult gatewayResult;
        try {
            payout = ensureProviderPayloadBound(paymentTransaction, payout);
            String providerCode = payout.getProviderCode();
            PayoutGateway.PayoutRequest payoutRequest = buildPayoutRequest(
                    paymentTransaction,
                    payout,
                    payoutAmount);
            PayoutGateway payoutGateway = resolveGateway(providerCode);
            gatewayResult = payoutGateway.requestPayout(payoutRequest);
        } catch (RuntimeException e) {
            String failureCode = resolveFailureCode(e);
            try {
                if (isAmbiguousPayoutFailure(e, failureCode)) {
                    keepPayoutRequested(
                            paymentTransaction,
                            payout,
                            failureCode,
                            String.valueOf(e.getMessage()),
                            true);
                } else {
                    markPayoutFailed(
                            paymentTransaction,
                            payout,
                            failureCode,
                            String.valueOf(e.getMessage()),
                            true);
                }
            } catch (RuntimeException persistenceFailure) {
                log.error("[Payout] provider failure state persistence failed; durable claim remains REQUESTED: payoutId={}",
                        payout.getId(), persistenceFailure);
            }
            log.error("[Payout] payout failed: paymentTransactionId={}, payoutId={}",
                    paymentTransaction.getId(), payout.getId(), e);
            throw e;
        }

        try {
            payout.setProviderRef(gatewayResult.providerRef());
            String rawStatus = normalizeProviderStatus(gatewayResult.rawStatus());
            if (isProviderCompleted(rawStatus)) {
                payout = markPayoutCompleted(paymentTransaction, payout);
            } else if (isProviderDefinitivelyFailed(rawStatus)) {
                payout = markPayoutFailed(
                        paymentTransaction,
                        payout,
                        "PAYOUT_PROVIDER_STATUS_" + rawStatus,
                        "providerStatus=" + rawStatus,
                        true);
            } else {
                payout = keepPayoutRequested(
                        paymentTransaction,
                        payout,
                        null,
                        "providerStatus=" + rawStatus,
                        false);
            }
        } catch (RuntimeException localFailure) {
            log.error("[Payout] provider response persistence failed; durable claim remains conservative: payoutId={}",
                    payout.getId(), localFailure);
            throw localFailure;
        }

        return payout;
    }

    private void reconcileRequestedPayout(
            PaymentTransaction paymentTransaction,
            PayoutTransaction payout) {
        PayoutGateway.PayoutStatusResult providerResult;
        try {
            payout = ensureProviderPayloadBound(paymentTransaction, payout);
            providerResult = resolveGateway(payout.getProviderCode())
                    .getPayoutStatus(payout.getProviderRef());
        } catch (RuntimeException lookupFailure) {
            String failureCode = resolveFailureCode(lookupFailure);
            try {
                keepPayoutRequested(
                        paymentTransaction,
                        payout,
                        failureCode,
                        String.valueOf(lookupFailure.getMessage()),
                        true);
            } catch (RuntimeException persistenceFailure) {
                log.error("[Payout] status lookup failure persistence failed; durable claim remains REQUESTED: payoutId={}",
                        payout.getId(), persistenceFailure);
            }
            log.error("[Payout] provider status lookup failed: payoutId={}", payout.getId(), lookupFailure);
            return;
        }

        try {
            if (providerResult != null
                    && providerResult.providerRef() != null
                    && !providerResult.providerRef().isBlank()) {
                payout.setProviderRef(providerResult.providerRef());
            }
            String rawStatus = normalizeProviderStatus(
                    providerResult != null ? providerResult.rawStatus() : null);
            if (isProviderCompleted(rawStatus)) {
                markPayoutCompleted(paymentTransaction, payout);
            } else if (isProviderDefinitivelyFailed(rawStatus)) {
                String failureCode = providerResult != null && providerResult.failureCode() != null
                        ? providerResult.failureCode()
                        : "PAYOUT_PROVIDER_STATUS_" + rawStatus;
                markPayoutFailed(
                        paymentTransaction,
                        payout,
                        failureCode,
                        providerResult != null ? providerResult.failureMessage() : null,
                        true);
            } else {
                keepPayoutRequested(
                        paymentTransaction,
                        payout,
                        providerResult != null ? providerResult.failureCode() : null,
                        providerResult != null ? providerResult.failureMessage() : "providerStatus=" + rawStatus,
                        true);
            }
        } catch (RuntimeException localFailure) {
            log.error("[Payout] provider status persistence failed; durable claim remains conservative: payoutId={}",
                    payout.getId(), localFailure);
            throw localFailure;
        }
    }

    private PayoutTransaction markPayoutCompleted(
            PaymentTransaction paymentTransaction,
            PayoutTransaction payout) {
        payout = payoutStateService.complete(paymentTransaction, payout, payout.getProviderRef());
        log.info("[Payout] payout completed: paymentTransactionId={}, payoutId={}",
                paymentTransaction.getId(), payout.getId());
        return payout;
    }

    private PayoutTransaction keepPayoutRequested(
            PaymentTransaction paymentTransaction,
            PayoutTransaction payout,
            String failureCode,
            String failReason,
            boolean incrementRetryCount) {
        payout = payoutStateService.keepRequested(
                paymentTransaction,
                payout,
                payout.getProviderRef(),
                failureCode,
                failReason,
                incrementRetryCount);
        if (payout.getNextRetryAt() != null) {
            scheduleRetry(payout.getId(), payout.getNextRetryAt());
        }
        return payout;
    }

    private PayoutTransaction markPayoutFailed(
            PaymentTransaction paymentTransaction,
            PayoutTransaction payout,
            String failureCode,
            String failReason,
            boolean incrementRetryCount) {
        payout = payoutStateService.fail(
                paymentTransaction,
                payout,
                payout.getProviderRef(),
                failureCode,
                failReason,
                incrementRetryCount,
                false);
        return payout;
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

    private PayoutGateway resolveGateway(String providerCode) {
        PayoutGateway gateway = payoutGateways.get(providerCode);
        if (gateway == null) {
            throw new IllegalStateException("지원되지 않는 지급대행 provider: " + providerCode);
        }
        return gateway;

    }

    private PayoutGateway.PayoutRequest buildPayoutRequest(
            PaymentTransaction paymentTransaction,
            PayoutTransaction payout,
            int payoutAmount) {
        return new PayoutGateway.PayoutRequest(
                paymentTransaction.getId(),
                paymentTransaction.getOrderId(),
                paymentTransaction.getSellerUserId(),
                payout.getProviderSellerId(),
                payoutAmount,
                "KRW",
                "mate-payout-" + paymentTransaction.getId());
    }

    private PayoutTransaction ensureProviderPayloadBound(
            PaymentTransaction paymentTransaction,
            PayoutTransaction payout) {
        String providerCode = payout.getProviderCode() == null || payout.getProviderCode().isBlank()
                ? resolveProviderCode()
                : payout.getProviderCode().trim().toUpperCase(Locale.ROOT);
        String providerSellerId = payout.getProviderSellerId();
        if ("TOSS".equals(providerCode)
                && (providerSellerId == null || providerSellerId.isBlank())) {
            providerSellerId = sellerPayoutProfileService.getRequiredProviderSellerId(
                    paymentTransaction.getSellerUserId(),
                    providerCode);
        }
        return payoutClaimService.bindProviderPayload(
                paymentTransaction.getId(),
                payout.getId(),
                providerCode,
                providerSellerId);
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

    private String normalizeProviderStatus(String rawStatus) {
        return Objects.toString(rawStatus, "UNKNOWN").trim().toUpperCase(Locale.ROOT);
    }

    private boolean isProviderCompleted(String rawStatus) {
        return "COMPLETED".equals(rawStatus);
    }

    private boolean isProviderDefinitivelyFailed(String rawStatus) {
        return "FAILED".equals(rawStatus)
                || "REJECTED".equals(rawStatus)
                || "CANCELED".equals(rawStatus)
                || "CANCELLED".equals(rawStatus)
                || "DELETED".equals(rawStatus);
    }

    private boolean isAmbiguousPayoutFailure(String failureCode) {
        return "TOSS_PAYOUT_REQUEST_FAILED".equals(failureCode)
                || "TOSS_PAYOUT_EMPTY_RESPONSE".equals(failureCode)
                || "TOSS_PAYOUT_NO_PROVIDER_REF".equals(failureCode);
    }

    private boolean isAmbiguousPayoutFailure(RuntimeException exception, String failureCode) {
        if (isAmbiguousPayoutFailure(failureCode)) {
            return true;
        }
        if ("SELLER_PROFILE_MISSING".equals(failureCode)) {
            return false;
        }
        if (exception instanceof PayoutGatewayException payoutGatewayException) {
            return !("TOSS_SELLER_INVALID".equals(failureCode)
                    && payoutGatewayException.getStatusCode() != null
                    && payoutGatewayException.getStatusCode().value() == 400);
        }
        return true;
    }

    @Transactional(readOnly = true)
    public java.util.Optional<PayoutTransaction> findLatestByPaymentTransactionId(Long paymentTransactionId) {
        if (paymentTransactionId == null) {
            return java.util.Optional.empty();
        }
        return payoutTransactionRepository.findTopByPaymentTransactionIdOrderByIdDesc(paymentTransactionId);
    }

}
