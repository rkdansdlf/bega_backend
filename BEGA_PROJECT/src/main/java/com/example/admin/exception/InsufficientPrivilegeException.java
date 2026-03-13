package com.example.admin.exception;

import com.example.common.exception.ForbiddenBusinessException;

/**
 * 권한 부족 예외
 * SUPER_ADMIN만 수행할 수 있는 작업을 일반 ADMIN이 시도할 때 발생
 */
public class InsufficientPrivilegeException extends ForbiddenBusinessException {

    public InsufficientPrivilegeException(String message) {
        super("INSUFFICIENT_PRIVILEGE", message);
    }

    public InsufficientPrivilegeException() {
        super("INSUFFICIENT_PRIVILEGE", "해당 작업을 수행할 권한이 없습니다. SUPER_ADMIN 권한이 필요합니다.");
    }
}
