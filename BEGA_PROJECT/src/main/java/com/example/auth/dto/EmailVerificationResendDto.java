package com.example.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailVerificationResendDto(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "유효한 이메일을 입력해주세요.")
        @Size(max = 320) String email) {
}
