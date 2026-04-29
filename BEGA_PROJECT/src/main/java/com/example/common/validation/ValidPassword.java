package com.example.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * 비밀번호 복잡도 검증 어노테이션
 * 최소 12자, 최대 72자, 대소문자, 숫자, 특수문자 포함
 */
@Documented
@Constraint(validatedBy = PasswordConstraintValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {
    
    String message() default "비밀번호는 12자 이상 72자 이하이며, 대문자, 소문자, 숫자, 특수문자를 포함해야 합니다.";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
}
