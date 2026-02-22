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
import com.example.mate.exception.PartyNotFoundException;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
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
    private final PartyRepository partyRepository;
    private final TossPaymentService tossPaymentService;
    private final CancelPolicyService cancelPolicyService;
    private final PayoutService payoutService;
    private final PaymentMetricsService metricsService;

    @Transactional
    public PaymentTransaction createOrGetOnConfirm(PartyApplication application, PaymentIntent intent, String paymentKey) {
        if (application == null || application.getOrderId() == null || application.getOrderId().isBlank()) {
            throw new IllegalArgumentException("orderId는 필수입니다.");
        }
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new IllegalArgumentException("paymentKey는 필수입니다.");
        }

        try {
            PaymentTransaction existing = findExistingTransaction(application.getOrderId(), paymentKey);
            if (existing != null) {
                validateExistingTransactionForRetry(existing, application, intent, paymentKey);
                return existing;
            }

            return createPaymentTransaction(application, intent, paymentKey);
        } catch (DataIntegrityViolationException ex) {
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
        PaymentTransaction tx = paymentTransactionRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("결제 트랜잭션을 찾을 수 없습니다: " + paymentId));
        tx.setSettlementStatus(SettlementStatus.REQUESTED);
        paymentTransactionRepository.save(tx);
        return payoutService.requestPayout(tx);
    }

    @Transactional(readOnly = true)
    public void enrichResponse(PartyApplicationDTO.Response response) {
        if (response == null || response.getOrderId() == null || response.getOrderId().isBlank()) {
            return;
        }
        paymentTransactionRepository.findByOrderId(response.getOrderId())
                .ifPresent(tx -> applyTransactionFields(response, tx));
    }

    @Transactional(readOnly = true)
    public void enrichResponses(List<PartyApplicationDTO.Response> responses) {
        if (responses == null || responses.isEmpty()) {
            return;
        }
        List<String> orderIds = responses.stream()
                .map(PartyApplicationDTO.Response::getOrderId)
                .filter(Objects::nonNull)
                .filter(orderId -> !orderId.isBlank())
                .distinct()
                .toList();
        if (orderIds.isEmpty()) {
            return;
        }

        Map<String, PaymentTransaction> byOrderId = paymentTransactionRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.toMap(PaymentTransaction::getOrderId, tx -> tx, (a, b) -> a, HashMap::new));

        responses.forEach(response -> {
            PaymentTransaction tx = byOrderId.get(response.getOrderId());
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

    @Transactional
    public PartyApplicationDTO.CancelResponse processCancellation(
            PartyApplication application,
            PartyApplicationDTO.CancelRequest request) {
        if (application == null || application.getOrderId() == null || application.getOrderId().isBlank()) {
            return PartyApplicationDTO.CancelResponse.builder()
                    .applicationId(application != null ? application.getId() : null)
                    .refundAmount(0)
                    .feeCharged(0)
                    .refundPolicyApplied("NO_PAYMENT")
                    .paymentStatus(null)
                    .settlementStatus(null)
                    .build();
        }

        PaymentTransaction tx = paymentTransactionRepository.findByOrderId(application.getOrderId())
                .orElseGet(() -> {
                    if (application.getPaymentKey() == null || application.getPaymentKey().isBlank()) {
                        return null;
                    }
                    return createFallbackTransaction(application);
                });

        if (tx == null) {
            return PartyApplicationDTO.CancelResponse.builder()
                    .applicationId(application.getId())
                    .refundAmount(0)
                    .feeCharged(0)
                    .refundPolicyApplied("NO_PAYMENT")
                    .paymentStatus(null)
                    .settlementStatus(null)
                    .build();
        }

        if (tx.getPaymentStatus() == PaymentStatus.CANCELED) {
            return buildCancelResponse(application.getId(), tx);
        }

        CancelReasonType reasonType = request != null && request.getCancelReasonType() != null
                ? request.getCancelReasonType()
                : CancelReasonType.BUYER_CHANGED_MIND;
        String cancelMemo = request != null ? request.getCancelMemo() : null;

        CancelPolicyService.RefundDecision decision = cancelPolicyService.decide(
                tx.getGrossAmount(),
                reasonType);

        tx.setCancelReasonType(reasonType);
        tx.setCancelMemo(cancelMemo);
        tx.setRefundPolicyApplied(decision.policyApplied());
        tx.setPaymentStatus(PaymentStatus.REFUND_REQUESTED);
        paymentTransactionRepository.save(tx);

        try {
            tossPaymentService.cancelPayment(
                    tx.getPaymentKey(),
                    "메이트 취소 처리: " + reasonType,
                    decision.refundAmount());

            tx.setRefundAmount(decision.refundAmount());
            tx.setFeeAmount(decision.feeAmount());
            tx.setNetAmount(decision.feeAmount()); // 취소 수수료가 있는 경우 해당 금액이 순정산액
            tx.setPaymentStatus(PaymentStatus.CANCELED);
            if (tx.getSettlementStatus() == SettlementStatus.COMPLETED) {
                // 판매자에게 이미 지급 완료된 후 환불: 판매자로부터 환수 필요
                tx.setSettlementStatus(SettlementStatus.REFUNDED_AFTER_SETTLEMENT);
            } else {
                // 아직 지급되지 않은 경우: 정산 건너뜀
                tx.setSettlementStatus(SettlementStatus.SKIPPED);
            }
            paymentTransactionRepository.save(tx);
            metricsService.recordRefund(decision.policyApplied());
            return buildCancelResponse(application.getId(), tx);
        } catch (RuntimeException e) {
            tx.setPaymentStatus(PaymentStatus.REFUND_FAILED);
            paymentTransactionRepository.save(tx);
            metricsService.recordRefund("failed");
            throw e;
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

    private PaymentTransaction createFallbackTransaction(PartyApplication application) {
        Party party = partyRepository.findById(application.getPartyId())
                .orElseThrow(() -> new PartyNotFoundException(application.getPartyId()));
        int grossAmount = application.getDepositAmount() != null ? application.getDepositAmount() : 0;
        PaymentTransaction tx = PaymentTransaction.builder()
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
        return paymentTransactionRepository.save(tx);
    }

    private PaymentFlowType inferFlowType(PartyApplication.PaymentType paymentType) {
        if (paymentType == PartyApplication.PaymentType.FULL) {
            return PaymentFlowType.SELLING_FULL;
        }
        return PaymentFlowType.DEPOSIT;
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
