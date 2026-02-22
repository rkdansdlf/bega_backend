package com.example.mate.service;

import com.example.mate.entity.CancelReasonType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CancelPolicyService {

    @Value("${mate.payment.fee-rate:0.10}")
    private double feeRate;

    public RefundDecision decide(int grossAmount, CancelReasonType reasonType) {
        CancelReasonType reason = reasonType == null ? CancelReasonType.OTHER : reasonType;
        boolean feeApplies = reason == CancelReasonType.BUYER_CHANGED_MIND
                || reason == CancelReasonType.SELLER_CHANGED_MIND;

        int fee = feeApplies ? calculateFee(grossAmount) : 0;
        int refundAmount = Math.max(0, grossAmount - fee);
        String policy = feeApplies
                ? "PARTIAL_REFUND_WITH_FEE"
                : "FULL_REFUND";

        return new RefundDecision(refundAmount, fee, policy);
    }

    private int calculateFee(int grossAmount) {
        if (grossAmount <= 0) {
            return 0;
        }
        return (int) Math.floor(grossAmount * feeRate);
    }

    public record RefundDecision(int refundAmount, int feeAmount, String policyApplied) {
    }
}
