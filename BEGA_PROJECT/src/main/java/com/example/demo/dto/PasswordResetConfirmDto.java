package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetConfirmDto {
    @NotBlank(message = "토큰은 필수입니다.")
    private String token;
    
    @NotBlank(message = "새 비밀번호는 필수입니다.")
    private String newPassword;
    
    @NotBlank(message = "비밀번호 확인은 필수입니다.")
    private String confirmPassword;
}
