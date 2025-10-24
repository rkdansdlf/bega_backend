package com.example.demo.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Getter
@Setter
@NoArgsConstructor
public class LoginDto {
    
    // 1. 이메일 (로그인 ID)
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;

    // 2. 비밀번호
    @NotBlank(message = "비밀번호는 필수입니다.")
    // 회원가입 DTO와 일관성을 위해 최소 길이를 유지했습니다.
    @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다.") 
    private String password;
}
