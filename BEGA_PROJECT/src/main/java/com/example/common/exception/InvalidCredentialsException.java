package com.example.common.exception;

public class InvalidCredentialsException extends UnauthorizedBusinessException {
    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", "이메일 또는 비밀번호가 일치하지 않습니다.");
    }
    
    public InvalidCredentialsException(String message) {
        super("INVALID_CREDENTIALS", message);
    }
}
  
