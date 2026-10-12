package com.example.admin.dto;

import java.time.Instant;
import java.util.List;

public record PaymentIntentReconciliationReviewDto(
        Long intentId,
        String orderId,
        Integer expectedAmount,
        String currency,
        Instant detectedAt,
        String requestedPaymentKey,
        String candidatePaymentKey,
        String candidateOrderId,
        Integer candidateAmount,
        String candidateStatus,
        List<Event> events) {

    public record Event(
            String eventType,
            String candidatePaymentKey,
            String candidateOrderId,
            Integer candidateAmount,
            String candidateStatus,
            String verifiedPaymentKey,
            String verifiedOrderId,
            Integer verifiedAmount,
            String verifiedStatus,
            String evidenceReference,
            Long recordedBy,
            Instant createdAt) {}
}
