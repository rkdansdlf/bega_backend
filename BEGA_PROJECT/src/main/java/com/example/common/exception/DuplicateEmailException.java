package com.example.common.exception;

public class DuplicateEmailException extends ConflictBusinessException {
    public DuplicateEmailException(String email) {
        super("DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다: " + email);
    }
}
