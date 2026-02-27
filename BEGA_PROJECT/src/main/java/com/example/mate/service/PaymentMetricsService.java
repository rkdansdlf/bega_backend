package com.example.mate.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class PaymentMetricsService {

    private final Counter confirmSuccess;
    private final Counter confirmFail;
    private final Counter confirmRetry;

    private final Counter compensationSuccess;
    private final Counter compensationFail;
    private final Counter compensationRequested;

    private final Counter refundPartial;
    private final Counter refundFull;
    private final Counter refundFailed;

    private final Counter payoutSuccess;
    private final Counter payoutFail;
    private final Counter payoutSkip;

    public PaymentMetricsService(MeterRegistry meterRegistry) {
        this.confirmSuccess = Counter.builder("mate_payment_confirm_total")
                .description("Toss 결제 승인 처리 건수")
                .tag("result", "success")
                .register(meterRegistry);

        this.confirmFail = Counter.builder("mate_payment_confirm_total")
                .description("Toss 결제 승인 실패 건수")
                .tag("result", "fail")
                .register(meterRegistry);

        this.confirmRetry = Counter.builder("mate_payment_confirm_total")
                .description("Toss 결제 승인 재시도(중복 요청) 건수")
                .tag("result", "retry")
                .register(meterRegistry);

        this.compensationSuccess = Counter.builder("mate_payment_compensation_total")
                .description("결제 취소 보상 처리 성공 건수")
                .tag("result", "success")
                .register(meterRegistry);

        this.compensationFail = Counter.builder("mate_payment_compensation_total")
                .description("결제 취소 보상 처리 실패 건수")
                .tag("result", "fail")
                .register(meterRegistry);

        this.compensationRequested = Counter.builder("mate_payment_compensation_requested_total")
                .description("결제 취소 보상 처리 시도 건수")
                .register(meterRegistry);

        this.refundPartial = Counter.builder("mate_refund_total")
                .description("부분환불 건수")
                .tag("policy", "partial")
                .register(meterRegistry);

        this.refundFull = Counter.builder("mate_refund_total")
                .description("전액환불 건수")
                .tag("policy", "full")
                .register(meterRegistry);

        this.refundFailed = Counter.builder("mate_refund_total")
                .description("환불 처리 실패 건수")
                .tag("policy", "failed")
                .register(meterRegistry);

        this.payoutSuccess = Counter.builder("mate_settlement_payout_total")
                .description("정산 지급 처리 성공 건수")
                .tag("result", "success")
                .register(meterRegistry);

        this.payoutFail = Counter.builder("mate_settlement_payout_total")
                .description("정산 지급 처리 실패 건수")
                .tag("result", "fail")
                .register(meterRegistry);

        this.payoutSkip = Counter.builder("mate_settlement_payout_total")
                .description("정산 지급 스킵 건수")
                .tag("result", "skip")
                .register(meterRegistry);
    }

    public void recordConfirm(String result) {
        switch (result) {
            case "success" -> confirmSuccess.increment();
            case "fail" -> confirmFail.increment();
            default -> confirmRetry.increment();
        }
    }

    public void recordCompensation(String result) {
        if ("success".equals(result)) {
            compensationSuccess.increment();
            return;
        }
        if ("fail".equals(result)) {
            compensationFail.increment();
        }
    }

    public void recordCompensationRequested() {
        compensationRequested.increment();
    }

    public void recordRefund(String policy) {
        if ("PARTIAL_REFUND_WITH_FEE".equals(policy)) {
            refundPartial.increment();
            return;
        }
        if ("FULL_REFUND".equals(policy)) {
            refundFull.increment();
            return;
        }
        refundFailed.increment();
    }

    public void recordPayout(String result) {
        if ("success".equals(result)) {
            payoutSuccess.increment();
            return;
        }
        if ("skip".equals(result)) {
            payoutSkip.increment();
            return;
        }
        payoutFail.increment();
    }
}
