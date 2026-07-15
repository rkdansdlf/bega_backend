package com.example.mate.service;

import com.example.mate.entity.CancelReasonType;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PaymentFlowType;
import com.example.mate.entity.PaymentStatus;
import com.example.mate.entity.PaymentTransaction;
import com.example.mate.entity.SettlementStatus;
import com.example.mate.exception.PartyNotFoundException;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PaymentCancellationIntentService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PartyRepository partyRepository;

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
