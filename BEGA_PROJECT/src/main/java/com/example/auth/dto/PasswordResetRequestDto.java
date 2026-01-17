package com.example.auth.dto;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetRequestDto {
    @Email(message = "유효한 이메일을 입력해주세요.")
    @NotBlank(message = "이메일은 필수입니다.")
    private String email;
}
