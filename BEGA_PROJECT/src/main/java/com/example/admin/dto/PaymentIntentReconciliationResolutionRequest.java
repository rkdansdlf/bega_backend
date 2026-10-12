package com.example.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentIntentReconciliationResolutionRequest {

    @NotNull
    private Outcome outcome;

    @NotBlank
    @Size(max = 300)
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:/-]{0,299}")
    private String evidenceReference;

    @NotBlank
    @Size(max = 200)
    private String verifiedPaymentKey;

    @NotBlank
    @Size(max = 100)
    private String verifiedOrderId;

    @NotNull
    @Positive
    private Integer verifiedAmount;

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
