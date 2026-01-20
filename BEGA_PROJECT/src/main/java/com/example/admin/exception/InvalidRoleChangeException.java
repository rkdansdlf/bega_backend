package com.example.admin.exception;

/**
 * 유효하지 않은 권한 변경 예외
 * 이미 해당 역할이거나, 자기 자신의 권한을 변경하려고 할 때 발생
 */
public class InvalidRoleChangeException extends RuntimeException {

    public InvalidRoleChangeException(String message) {
        super(message);
    }
}
