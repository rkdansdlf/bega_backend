package com.example.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OAuthEmailChallengeConfirmDto(
        @NotBlank @Size(max = 256) String token) {
}
