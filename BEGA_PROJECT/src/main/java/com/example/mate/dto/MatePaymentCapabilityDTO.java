package com.example.mate.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public final class MatePaymentCapabilityDTO {

    private MatePaymentCapabilityDTO() {
    }

    @Schema(name = "MatePaymentCapabilityResponse")
    public record Response(
            String paymentMode,
            String businessMode,
            String provider,
            String environment,
            boolean tossPaymentEnabled,
            boolean sellingPaymentRequired,
            boolean payoutEnabled,
            String payoutProvider) {
    }
}
