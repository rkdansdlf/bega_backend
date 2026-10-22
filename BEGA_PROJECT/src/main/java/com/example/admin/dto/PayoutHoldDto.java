package com.example.admin.dto;

import com.example.mate.entity.SettlementStatus;

import java.time.Instant;

public record PayoutHoldDto(
        Long payoutId,
        Long paymentTransactionId,
        SettlementStatus status,
        String reasonCode,
        Instant updatedAt) {
}
