package com.example.admin.dto;

public record PaymentIntentReconciliationResolutionResultDto(
        boolean awaitingIndependentReview,
        String outcome) {}
