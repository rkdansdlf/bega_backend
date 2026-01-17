package com.example.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * 비밀번호와 비밀번호 확인이 일치하는지 검증
 */
@Documented
@Constraint(validatedBy = PasswordMatchesValidator.class)
@Target({ElementType.TYPE})  // 🔥 클래스 레벨에 적용
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordMatches {
    
    String message() default "비밀번호와 비밀번호 확인이 일치하지 않습니다.";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
}
