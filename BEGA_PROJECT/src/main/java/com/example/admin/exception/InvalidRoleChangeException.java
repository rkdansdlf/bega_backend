package com.example.admin.exception;

import com.example.common.exception.BadRequestBusinessException;

/**
 * 유효하지 않은 권한 변경 예외
 * 이미 해당 역할이거나, 자기 자신의 권한을 변경하려고 할 때 발생
 */
public class InvalidRoleChangeException extends BadRequestBusinessException {

    public InvalidRoleChangeException(String message) {
        super("INVALID_ROLE_CHANGE", message);
    }
}
