package com.example.mate.service;

import com.example.mate.entity.CancelReasonType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class CancelPolicyServiceTest {

    private CancelPolicyService cancelPolicyService;

    @BeforeEach
    void setUp() {
        cancelPolicyService = new CancelPolicyService();
        ReflectionTestUtils.setField(cancelPolicyService, "feeRate", 0.10d);
    }

    @Test
    void decide_changedMind_appliesPartialRefundWithFee() {
        CancelPolicyService.RefundDecision decision = cancelPolicyService.decide(50000, CancelReasonType.BUYER_CHANGED_MIND);

        assertThat(decision.feeAmount()).isEqualTo(5000);
        assertThat(decision.refundAmount()).isEqualTo(45000);
        assertThat(decision.policyApplied()).isEqualTo("PARTIAL_REFUND_WITH_FEE");
    }

    @Test
    void decide_nonChangedMind_appliesFullRefund() {
        CancelPolicyService.RefundDecision decision = cancelPolicyService.decide(50000, CancelReasonType.SYSTEM);

        assertThat(decision.feeAmount()).isZero();
        assertThat(decision.refundAmount()).isEqualTo(50000);
        assertThat(decision.policyApplied()).isEqualTo("FULL_REFUND");
    }
}
