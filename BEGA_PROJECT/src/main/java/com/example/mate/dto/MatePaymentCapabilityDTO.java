package com.example.mate.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public final class MatePaymentCapabilityDTO {

    private MatePaymentCapabilityDTO() {
    }

    @Schema(name = "MatePaymentCapabilityResponse")
    public record Response(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = { "DIRECT_TRADE", "TOSS_TEST", "IN_APP_PAYMENT" })
            String paymentMode,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = { "DIRECT_TRADE", "IN_APP_PAYMENT" })
            String businessMode,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = { "TOSS", "UNSUPPORTED" })
            String provider,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = { "NONE", "TEST", "LIVE" })
            String environment,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean tossPaymentEnabled,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean sellingPaymentRequired,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean payoutEnabled,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    allowableValues = { "SIM", "TOSS", "UNSUPPORTED" })
            String payoutProvider) {
    }
}
