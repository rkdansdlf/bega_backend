package com.example.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountDeletionRecoveryRequestDto {

    @NotBlank(message = "복구 토큰이 필요합니다.")
    private String token;
}
