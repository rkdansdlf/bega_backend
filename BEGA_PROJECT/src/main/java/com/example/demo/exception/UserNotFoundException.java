package com.example.demo.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
    
    public UserNotFoundException(Long userId) {
        super("사용자를 찾을 수 없습니다. ID: " + userId);
    }
    
    public UserNotFoundException(String field, String value) {
        super(String.format("사용자를 찾을 수 없습니다. %s: %s", field, value));
    }
}
