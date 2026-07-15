package com.example.mate.service;

import com.example.mate.dto.PartyApplicationDTO;
import com.example.mate.entity.CancelReasonType;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentFlowType;
import com.example.mate.entity.PaymentIntent;
import com.example.mate.entity.PaymentStatus;
import com.example.mate.entity.PaymentTransaction;
import com.example.mate.entity.PayoutTransaction;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.exception.TossPaymentException;
import com.example.mate.exception.PartyNotFoundException;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PaymentTransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentTransactionService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PartyApplicationRepository partyApplicationRepository;
    private final PartyRepository partyRepository;
    private final TossPaymentService tossPaymentService;
    private final CancelPolicyService cancelPolicyService;
    private final PayoutService payoutService;
    private final PaymentMetricsService metricsService;
    private final MatePaymentModeService matePaymentModeService;
    private final SellerRecoveryService sellerRecoveryService;
    private final PaymentCancellationIntentService cancellationIntentService;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public PaymentTransaction createOrGetOnConfirm(PartyApplication application, PaymentIntent intent, String paymentKey) {
        if (application == null || application.getOrderId() == null || application.getOrderId().isBlank()) {
            throw new IllegalArgumentException("orderId는 필수입니다.");
        }
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new IllegalArgumentException("paymentKey는 필수입니다.");
        }

        partyApplicationRepository.findByOrderIdForUpdate(application.getOrderId());

        try {
            PaymentTransaction existing = findExistingTransaction(application.getOrderId(), paymentKey);
            if (existing != null) {
                validateExistingTransactionForRetry(existing, application, intent, paymentKey);
                return existing;
            }

            return createPaymentTransaction(application, intent, paymentKey);
        } catch (DataIntegrityViolationException ex) {
            if (entityManager != null) {
                entityManager.clear();
            }
            PaymentTransaction existing = findExistingTransaction(application.getOrderId(), paymentKey);
            if (existing != null) {
                validateExistingTransactionForRetry(existing, application, intent, paymentKey);
                return existing;
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public Optional<PaymentTransaction> findByOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return Optional.empty();
        }
        return paymentTransactionRepository.findByOrderId(orderId);
    }

    @Transactional(readOnly = true)
    public Optional<PaymentTransaction> findByPaymentId(Long paymentId) {
        return paymentTransactionRepository.findById(paymentId);
    }

    @Transactional
    public PayoutTransaction requestManualPayout(Long paymentId) {
        if (matePaymentModeService.isDirectTrade()) {
            throw new TossPaymentException(
                    "직거래 모드에서는 수동 정산 지급을 요청할 수 없습니다.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        PaymentTransaction tx = paymentTransactionRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("결제 트랜잭션을 찾을 수 없습니다: " + paymentId));
        if (tx.getPaymentStatus() == PaymentStatus.CANCELED) {
            throw new IllegalStateException("취소된 결제는 정산 지급을 요청할 수 없습니다.");
        }
        if (tx.getPaymentStatus() != PaymentStatus.PAID) {
            throw new IllegalStateException("결제 완료 상태에서만 정산 지급을 요청할 수 있습니다.");
        }
        tx.setSettlementStatus(SettlementStatus.REQUESTED);
        paymentTransactionRepository.save(tx);
        try {
            return payoutService.requestPayout(tx);
        } catch (RuntimeException e) {
            return payoutService.findLatestByPaymentTransactionId(tx.getId())
                    .orElseThrow(() -> e);
        }
    }

    @Transactional(readOnly = true)
    public void enrichResponse(PartyApplicationDTO.Response response) {
        if (response == null || response.getId() == null) {
            return;
        }
        paymentTransactionRepository.findByApplicationId(response.getId())
                .ifPresent(tx -> applyTransactionFields(response, tx));
    }

    @Transactional(readOnly = true)
    public void enrichResponses(List<PartyApplicationDTO.Response> responses) {
        if (responses == null || responses.isEmpty()) {
            return;
        }
        List<Long> applicationIds = responses.stream()
                .map(PartyApplicationDTO.Response::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (applicationIds.isEmpty()) {
            return;
        }

        Map<Long, PaymentTransaction> byApplicationId = paymentTransactionRepository.findByApplicationIdIn(applicationIds).stream()
                .collect(Collectors.toMap(PaymentTransaction::getApplicationId, tx -> tx, (a, b) -> a, HashMap::new));

        responses.forEach(response -> {
            PaymentTransaction tx = byApplicationId.get(response.getId());
            if (tx != null) {
                applyTransactionFields(response, tx);
            }
        });
    }

    @Transactional
    public void requestSettlementOnApproval(PartyApplication application) {
        if (application == null || application.getOrderId() == null || application.getOrderId().isBlank()) {
            return;
        }
        Optional<PaymentTransaction> optionalTx = paymentTransactionRepository.findByOrderId(application.getOrderId());
        if (optionalTx.isEmpty()) {
            return;
        }

        PaymentTransaction tx = optionalTx.get();
        if (tx.getPaymentStatus() != PaymentStatus.PAID) {
            return;
        }
        if (tx.getSettlementStatus() == SettlementStatus.COMPLETED
                || tx.getSettlementStatus() == SettlementStatus.REQUESTED) {
            return;
        }

        tx.setSettlementStatus(SettlementStatus.REQUESTED);
        paymentTransactionRepository.save(tx);
        try {
            payoutService.requestPayout(tx);
        } catch (RuntimeException e) {
            tx.setSettlementStatus(SettlementStatus.FAILED);
            paymentTransactionRepository.save(tx);
            metricsService.recordPayout("fail");
            log.error(
                    "[Payment] 승인 즉시 정산 처리 중 오류: applicationId={}, paymentTransactionId={}",
                    application.getId(),
                    tx.getId(),
                    e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = RuntimeException.class)
    public PartyApplicationDTO.CancelResponse processCancellation(
            PartyApplication application,
            PartyApplicationDTO.CancelRequest request) {
        if (application == null || !Boolean.TRUE.equals(application.getIsPaid())) {
            return PartyApplicationDTO.CancelResponse.builder()
                    .applicationId(application != null ? application.getId() : null)
                    .refundAmount(0)
                    .feeCharged(0)
                    .refundPolicyApplied("NO_PAYMENT")
                    .paymentStatus(null)
                    .settlementStatus(null)
                    .build();
        }

        if (application.getOrderId() == null || application.getOrderId().isBlank()) {
            throw new IllegalStateException("결제된 신청에 주문 ID가 없어 환불할 수 없습니다.");
        }

        PaymentTransaction tx = paymentTransactionRepository.findByOrderId(application.getOrderId())
                .orElse(null);

        if (tx == null && application.getPaymentKey() != null && !application.getPaymentKey().isBlank()) {
            tx = cancellationIntentService.createFallbackTransaction(application);
        }

        if (tx == null) {
            throw new IllegalStateException("결제된 신청의 결제 트랜잭션을 찾을 수 없어 환불할 수 없습니다.");
        }

        if (tx.getPaymentStatus() == PaymentStatus.CANCELED) {
            return buildCancelResponse(application.getId(), tx);
        }

        PaymentCancellationIntentService.CancellationIntent proposed = resolveCancellationIntent(tx, request);
        PaymentCancellationIntentService.PreparedCancellation prepared = cancellationIntentService.prepare(
                tx,
                proposed);
        tx = prepared.transaction();
        PaymentCancellationIntentService.CancellationIntent intent = prepared.intent();
        if (entityManager != null) {
            entityManager.clear();
        }
        if (tx.getPaymentStatus() == PaymentStatus.CANCELED) {
            return buildCancelResponse(application.getId(), tx);
        }

        try {
            ProviderCancellation currentCancellation = resolveProviderCancellation(
                    tx,
                    tossPaymentService.getPayment(tx.getPaymentKey()));
            if (!currentCancellation.verified()) {
                throw new IllegalStateException("결제사의 현재 취소 금액을 확인할 수 없습니다.");
            }
            if (currentCancellation.provesAtLeast(intent.refundAmount())) {
                return finalizeReconciledCancellation(
                        application.getId(),
                        tx,
                        intent,
                        currentCancellation);
            }
            int cancelAmount = Math.max(
                    0,
                    intent.refundAmount() - currentCancellation.canceledAmount());
            com.example.mate.dto.TossPaymentDTO.CancelResponse providerResponse = tossPaymentService.cancelPayment(
                    tx.getPaymentKey(),
                    "메이트 취소 처리: " + intent.reasonType(),
                    cancelAmount);

            ProviderCancellation cancellation = resolveProviderCancellation(tx, providerResponse);
            if (!cancellation.provesAtLeast(intent.refundAmount())) {
                cancellation = resolveProviderCancellation(tx, tossPaymentService.getPayment(tx.getPaymentKey()));
            }
            return finalizeReconciledCancellation(application.getId(), tx, intent, cancellation);
        } catch (RuntimeException e) {
            if (isAlreadyCancelledByProvider(e)) {
                try {
                    ProviderCancellation cancellation = resolveProviderCancellation(
                            tx,
                            tossPaymentService.getPayment(tx.getPaymentKey()));
                    return finalizeReconciledCancellation(application.getId(), tx, intent, cancellation);
                } catch (RuntimeException reconciliationFailure) {
                    tx.setPaymentStatus(PaymentStatus.REFUND_FAILED);
                    paymentTransactionRepository.save(tx);
                    metricsService.recordRefund("failed");
                    throw reconciliationFailure;
                }
            }
            tx.setPaymentStatus(PaymentStatus.REFUND_FAILED);
            paymentTransactionRepository.save(tx);
            metricsService.recordRefund("failed");
            throw e;
        }
    }

    private PaymentCancellationIntentService.CancellationIntent resolveCancellationIntent(
            PaymentTransaction tx,
            PartyApplicationDTO.CancelRequest request) {
        if (tx.getCancellationRequestedAt() != null
                && tx.getRequestedRefundAmount() != null
                && tx.getRequestedFeeAmount() != null
                && tx.getCancelReasonType() != null
                && tx.getRefundPolicyApplied() != null) {
            return new PaymentCancellationIntentService.CancellationIntent(
                    tx.getCancelReasonType(),
                    tx.getCancelMemo(),
                    tx.getRequestedRefundAmount(),
                    tx.getRequestedFeeAmount(),
                    tx.getRefundPolicyApplied(),
                    true);
        }

        CancelReasonType reasonType = request != null && request.getCancelReasonType() != null
                ? request.getCancelReasonType()
                : CancelReasonType.BUYER_CHANGED_MIND;
        CancelPolicyService.RefundDecision decision = cancelPolicyService.decide(tx.getGrossAmount(), reasonType);

        return new PaymentCancellationIntentService.CancellationIntent(
                reasonType,
                request != null ? request.getCancelMemo() : null,
                decision.refundAmount(),
                decision.feeAmount(),
                decision.policyApplied(),
                false);
    }

    private PartyApplicationDTO.CancelResponse finalizeReconciledCancellation(
            Long applicationId,
            PaymentTransaction tx,
            PaymentCancellationIntentService.CancellationIntent intent,
            ProviderCancellation cancellation) {
        if (!cancellation.provesAtLeast(intent.refundAmount())) {
            throw new IllegalStateException("결제사 취소 금액이 요청된 환불 금액보다 작습니다.");
        }

        boolean wasSettled = tx.getSettlementStatus() == SettlementStatus.COMPLETED;
        int originalPaidAmount = Math.max(
                0,
                Objects.requireNonNullElse(tx.getNetAmount(), tx.getGrossAmount()));
        int actualRefundAmount = Math.min(tx.getGrossAmount(), cancellation.canceledAmount());
        int actualFeeAmount = Math.max(0, tx.getGrossAmount() - actualRefundAmount);
        tx.setRefundAmount(actualRefundAmount);
        tx.setFeeAmount(actualFeeAmount);
        tx.setNetAmount(actualFeeAmount);
        tx.setPaymentStatus(PaymentStatus.CANCELED);
        tx.setProviderReconciledAt(Instant.now());
        if (wasSettled) {
            tx.setSettlementStatus(SettlementStatus.REFUNDED_AFTER_SETTLEMENT);
        } else {
            tx.setSettlementStatus(SettlementStatus.SKIPPED);
        }
        paymentTransactionRepository.save(tx);
        if (wasSettled) {
            sellerRecoveryService.recordSettledRefund(tx, originalPaidAmount);
        }
        metricsService.recordRefund(intent.policyApplied());
        return buildCancelResponse(applicationId, tx);
    }

    private ProviderCancellation resolveProviderCancellation(
            PaymentTransaction tx,
            com.example.mate.dto.TossPaymentDTO.CancelResponse response) {
        if (response == null || !Objects.equals(tx.getPaymentKey(), response.getPaymentKey())) {
            return ProviderCancellation.unverified();
        }
        return providerCancellation(
                response.getTotalAmount(),
                response.getBalanceAmount(),
                response.getCancels());
    }

    private ProviderCancellation resolveProviderCancellation(
            PaymentTransaction tx,
            com.example.mate.dto.TossPaymentDTO.ConfirmResponse response) {
        if (response == null || !Objects.equals(tx.getPaymentKey(), response.getPaymentKey())) {
            return ProviderCancellation.unverified();
        }
        return providerCancellation(
                response.getTotalAmount(),
                response.getBalanceAmount(),
                response.getCancels());
    }

    private ProviderCancellation providerCancellation(
            Integer totalAmount,
            Integer balanceAmount,
            List<com.example.mate.dto.TossPaymentDTO.CancelDetail> cancels) {
        if (cancels != null && !cancels.isEmpty()) {
            int canceledAmount = cancels.stream()
                    .filter(Objects::nonNull)
                    .filter(cancel -> isCompletedProviderCancellation(cancel.getCancelStatus()))
                    .map(com.example.mate.dto.TossPaymentDTO.CancelDetail::getCancelAmount)
                    .filter(Objects::nonNull)
                    .filter(amount -> amount > 0)
                    .mapToInt(Integer::intValue)
                    .sum();
            if (canceledAmount > 0) {
                return new ProviderCancellation(canceledAmount, true);
            }
        }
        if (totalAmount != null && balanceAmount != null
                && totalAmount >= 0 && balanceAmount >= 0 && balanceAmount <= totalAmount) {
            return new ProviderCancellation(totalAmount - balanceAmount, true);
        }
        return ProviderCancellation.unverified();
    }

    private boolean isCompletedProviderCancellation(String cancelStatus) {
        if (cancelStatus == null || cancelStatus.isBlank()) {
            return false;
        }
        return "DONE".equalsIgnoreCase(cancelStatus)
                || "COMPLETED".equalsIgnoreCase(cancelStatus);
    }

    private record ProviderCancellation(int canceledAmount, boolean verified) {
        private static ProviderCancellation unverified() {
            return new ProviderCancellation(0, false);
        }

        private boolean provesAtLeast(int requestedAmount) {
            return verified && canceledAmount >= requestedAmount;
        }
    }

    private PaymentTransaction createPaymentTransaction(PartyApplication application, PaymentIntent intent, String paymentKey) {
        Party party = partyRepository.findById(application.getPartyId())
                .orElseThrow(() -> new PartyNotFoundException(application.getPartyId()));

        int grossAmount = application.getDepositAmount() != null ? application.getDepositAmount() : 0;
        PaymentFlowType flowType = resolveFlowType(application, intent);

        PaymentTransaction tx = PaymentTransaction.builder()
                .partyId(application.getPartyId())
                .applicationId(application.getId())
                .buyerUserId(application.getApplicantId())
                .sellerUserId(party.getHostId())
                .flowType(flowType)
                .orderId(application.getOrderId())
                .paymentKey(paymentKey)
                .grossAmount(grossAmount)
                .feeAmount(0)
                .refundAmount(0)
                .netAmount(grossAmount)
                .paymentStatus(PaymentStatus.PAID)
                .settlementStatus(SettlementStatus.PENDING)
                .build();

        return paymentTransactionRepository.save(tx);
    }

    private PaymentFlowType resolveFlowType(PartyApplication application, PaymentIntent intent) {
        if (intent != null && intent.getFlowType() != null) {
            return intent.getFlowType();
        }
        return inferFlowType(application.getPaymentType());
    }

    private PaymentFlowType inferFlowType(PartyApplication.PaymentType paymentType) {
        if (paymentType == PartyApplication.PaymentType.FULL) {
            return PaymentFlowType.SELLING_FULL;
        }
        return PaymentFlowType.DEPOSIT;
    }

    private boolean isAlreadyCancelledByProvider(RuntimeException ex) {
        if (!(ex instanceof TossPaymentException tossEx)) {
            return false;
        }

        String tossErrorCode = tossEx.getTossErrorCode();
        if (tossErrorCode != null) {
            return "ALREADY_CANCELED_PAYMENT".equals(tossErrorCode)
                    || "ALREADY_FULLY_CANCELED".equals(tossErrorCode)
                    || "PAYMENT_ALREADY_CANCELED".equals(tossErrorCode);
        }

        HttpStatus status = tossEx.getStatusCode() instanceof HttpStatus
                ? (HttpStatus) tossEx.getStatusCode()
                : null;
        boolean conflictStatus = status == HttpStatus.CONFLICT || status == HttpStatus.NOT_FOUND;
        String message = tossEx.getMessage() == null ? "" : tossEx.getMessage().toLowerCase();
        return conflictStatus && (message.contains("already") || message.contains("이미 취소된"));
    }

    private void applyTransactionFields(PartyApplicationDTO.Response response, PaymentTransaction tx) {
        response.setFeeAmount(tx.getFeeAmount());
        response.setNetSettlementAmount(tx.getNetAmount());
        response.setPaymentStatus(tx.getPaymentStatus());
        response.setSettlementStatus(tx.getSettlementStatus());
    }

    private PartyApplicationDTO.CancelResponse buildCancelResponse(Long applicationId, PaymentTransaction tx) {
        return PartyApplicationDTO.CancelResponse.builder()
                .applicationId(applicationId)
                .refundAmount(tx.getRefundAmount())
                .feeCharged(tx.getFeeAmount())
                .refundPolicyApplied(tx.getRefundPolicyApplied())
                .paymentStatus(tx.getPaymentStatus())
                .settlementStatus(tx.getSettlementStatus())
                .build();
    }

    private PaymentTransaction findExistingTransaction(String orderId, String paymentKey) {
        PaymentTransaction existingByOrderId = paymentTransactionRepository.findByOrderIdForUpdate(orderId)
                .orElse(null);
        if (existingByOrderId != null) {
            return existingByOrderId;
        }

        return paymentTransactionRepository.findByPaymentKeyForUpdate(paymentKey)
                .orElse(null);
    }

    private void validateExistingTransactionForRetry(
            PaymentTransaction tx,
            PartyApplication application,
            PaymentIntent intent,
            String paymentKey) {
        validateExistingTransactionForRetry(tx);

        if (!application.getOrderId().equals(tx.getOrderId())) {
            throw new IllegalStateException("기존 결제 주문 정보가 일치하지 않습니다.");
        }

        if (!paymentKey.equals(tx.getPaymentKey())) {
            throw new IllegalStateException("기존 결제 키가 일치하지 않습니다.");
        }

        PaymentFlowType expectedFlowType = resolveFlowType(application, intent);
        if (tx.getFlowType() != expectedFlowType) {
            throw new IllegalStateException("기존 결제 흐름과 요청 흐름이 일치하지 않습니다.");
        }

        Integer expectedGrossAmount = application.getDepositAmount();
        if (expectedGrossAmount == null) {
            throw new IllegalStateException("결제 금액 정보가 올바르지 않습니다.");
        }

        if (!expectedGrossAmount.equals(tx.getGrossAmount())) {
            throw new IllegalStateException("기존 결제 금액이 변경되어 재시도할 수 없습니다.");
        }
    }

    private void validateExistingTransactionForRetry(PaymentTransaction tx) {
        if (tx.getPaymentStatus() == PaymentStatus.CANCELED) {
            throw new IllegalStateException("기존 결제가 취소되어 재요청할 수 없습니다.");
        }
        if (tx.getPaymentStatus() == PaymentStatus.REFUND_FAILED) {
            throw new IllegalStateException("기존 결제의 환불이 실패되어 재요청할 수 없습니다.");
        }
    }
}
