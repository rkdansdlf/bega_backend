package com.example.mypage.dto;

import com.example.common.validation.PasswordMatches;
import com.example.common.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@PasswordMatches
public class ChangePasswordRequest {

    private String currentPassword;

    @NotBlank(message = "새 비밀번호를 입력해주세요.")
    @ValidPassword
    private String newPassword;

    @NotBlank(message = "비밀번호 확인을 입력해주세요.")
    private String confirmPassword;
}
