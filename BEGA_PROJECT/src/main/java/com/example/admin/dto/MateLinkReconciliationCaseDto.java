package com.example.admin.dto;

import com.example.mate.entity.MateLinkReconciliationReasonCode;

import java.time.Instant;

public record MateLinkReconciliationCaseDto(
        Long id,
        Long partyId,
        Long applicationId,
        Long paymentTransactionId,
        MateLinkReconciliationReasonCode reasonCode,
        Instant observedAt) {
}
