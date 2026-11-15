package com.example.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record PaymentIntentReconciliationResolutionResultDto(
        @Schema(description = "True until a different administrator records the same proposal; this review does not resolve the payment.")
        boolean awaitingIndependentReview,
        @Schema(description = "Always true for this endpoint until trusted provider proof is independently obtained.")
        boolean awaitingTrustedProviderProof,
        @Schema(description = "The requested proposal type, not a verified provider outcome.")
        String outcome) {}
