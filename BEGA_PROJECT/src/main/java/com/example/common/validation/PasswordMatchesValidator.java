package com.example.common.validation;

import com.example.auth.dto.PasswordResetConfirmDto;
import com.example.auth.dto.SignupDto;
import com.example.mypage.dto.ChangePasswordRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 비밀번호 일치 검증 구현
 * SignupDto와 PasswordResetConfirmDto에 모두 사용 가능
 */
public class PasswordMatchesValidator implements ConstraintValidator<PasswordMatches, Object> {

    @Override
    public void initialize(PasswordMatches constraintAnnotation) {
        // 초기화 로직
    }

    @Override
    public boolean isValid(Object obj, ConstraintValidatorContext context) {
        if (obj == null) {
            return true;
        }

        String password = null;
        String confirmPassword = null;

        // SignupDto인 경우
        if (obj instanceof SignupDto) {
            SignupDto dto = (SignupDto) obj;
            password = dto.getPassword();
            confirmPassword = dto.getConfirmPassword();
        }
        // PasswordResetConfirmDto인 경우
        else if (obj instanceof PasswordResetConfirmDto) {
            PasswordResetConfirmDto dto = (PasswordResetConfirmDto) obj;
            password = dto.getNewPassword();
            confirmPassword = dto.getConfirmPassword();
        }
        else if (obj instanceof ChangePasswordRequest) {
            ChangePasswordRequest dto = (ChangePasswordRequest) obj;
            password = dto.getNewPassword();
            confirmPassword = dto.getConfirmPassword();
        }

        // 비밀번호 일치 여부 확인
        if (password == null || confirmPassword == null || password.equals(confirmPassword)) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("confirmPassword")
                .addConstraintViolation();
        return false;
    }
}
