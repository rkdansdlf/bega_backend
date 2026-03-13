package com.example.common.exception;

public class UserNotFoundException extends NotFoundBusinessException {
    public UserNotFoundException(String message) {
        super("USER_NOT_FOUND", message);
    }
    
    public UserNotFoundException(Long userId) {
        super("USER_NOT_FOUND", "사용자를 찾을 수 없습니다. ID: " + userId);
    }
    
    public UserNotFoundException(String field, String value) {
        super("USER_NOT_FOUND", String.format("사용자를 찾을 수 없습니다. %s: %s", field, value));
    }
}
