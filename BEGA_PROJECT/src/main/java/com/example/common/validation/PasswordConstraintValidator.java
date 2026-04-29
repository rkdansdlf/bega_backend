package com.example.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 비밀번호 복잡도 검증 구현
 */
public class PasswordConstraintValidator implements ConstraintValidator<ValidPassword, String> {

    private static final int MIN_LENGTH = 12;
    private static final int MAX_LENGTH = 72;

    // 정규식: 소문자, 대문자, 숫자, 특수문자 각 1개 이상
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#])[A-Za-z\\d@$!%*?&#]+$"
    );

    private final CompromisedPasswordChecker compromisedPasswordChecker;

    public PasswordConstraintValidator() {
        this(null);
    }

    @Autowired
    public PasswordConstraintValidator(CompromisedPasswordChecker compromisedPasswordChecker) {
        this.compromisedPasswordChecker = compromisedPasswordChecker;
    }

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

        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            return false;
        }

        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            return false;
        }

        return compromisedPasswordChecker == null
                || !compromisedPasswordChecker.isCompromised(password);
    }
}
