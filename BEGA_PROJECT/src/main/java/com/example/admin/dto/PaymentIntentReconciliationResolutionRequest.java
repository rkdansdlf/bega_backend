package com.example.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentIntentReconciliationResolutionRequest {

    @Schema(description = "Administrator-proposed outcome only; this field does not assert a provider-verified result.")
    @NotNull
    private Outcome outcome;

    @Schema(description = "Opaque administrator audit reference. The server does not dereference or verify this reference.")
    @NotBlank
    @Size(max = 300)
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:/-]{0,299}")
    private String evidenceReference;

    @Schema(description = "Administrator-submitted candidate payment key; not independently verified by the server.")
    @NotBlank
    @Size(max = 200)
    private String verifiedPaymentKey;

    @Schema(description = "Administrator-submitted candidate order ID; must match the internal payment intent.")
    @NotBlank
    @Size(max = 100)
    private String verifiedOrderId;

    @Schema(description = "Administrator-submitted candidate amount; must match the internal payment intent.")
    @NotNull
    @Positive
    private Integer verifiedAmount;

    @Schema(description = "Administrator-submitted candidate provider status; not independently verified by the server.")
    @NotBlank
    @Size(max = 30)
    @Pattern(regexp = "[A-Z_]{1,30}")
    private String verifiedStatus;

    public enum Outcome {
        VERIFIED_PAYMENT,
        VERIFIED_CANCELED,
        VERIFIED_REFUNDED
    }
}
