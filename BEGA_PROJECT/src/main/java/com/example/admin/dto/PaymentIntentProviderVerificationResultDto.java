package com.example.admin.dto;

public record PaymentIntentProviderVerificationResultDto(
        Long paymentIntentId,
        Outcome outcome) {

    public enum Outcome {
        VERIFIED,
        ALREADY_VERIFIED
    }
}
