package com.example.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * 비밀번호 복잡도 검증 구현
 */
public class PasswordConstraintValidator implements ConstraintValidator<ValidPassword, String> {

    // 정규식: 최소 8자, 소문자, 대문자, 숫자, 특수문자 각 1개 이상
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#])[A-Za-z\\d@$!%*?&#]{8,}$"
    );

    @Override
    public void initialize(ValidPassword constraintAnnotation) {
        // 초기화 로직 (필요시)
    }

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        // null이나 빈 문자열은 @NotBlank가 처리하므로 여기서는 패턴만 검증
        if (password == null || password.isEmpty()) {
            return true;
        }
        
        return PASSWORD_PATTERN.matcher(password).matches();
    }
}
