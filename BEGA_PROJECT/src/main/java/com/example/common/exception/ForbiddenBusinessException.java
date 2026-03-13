package com.example.common.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenBusinessException extends BusinessException {

    public ForbiddenBusinessException(String code, String message) {
        super(HttpStatus.FORBIDDEN, code, message);
    }

    public ForbiddenBusinessException(String code, String message, Object data) {
        super(HttpStatus.FORBIDDEN, code, message, data);
    }
}
