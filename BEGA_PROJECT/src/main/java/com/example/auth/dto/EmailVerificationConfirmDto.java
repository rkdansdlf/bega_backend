package com.example.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailVerificationConfirmDto(
        @NotBlank(message = "인증 토큰이 필요합니다.") @Size(max = 256) String token) {
}
