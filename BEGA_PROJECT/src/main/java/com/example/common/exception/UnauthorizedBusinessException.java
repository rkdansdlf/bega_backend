package com.example.common.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedBusinessException extends BusinessException {

    public UnauthorizedBusinessException(String code, String message) {
        super(HttpStatus.UNAUTHORIZED, code, message);
    }

    public UnauthorizedBusinessException(String code, String message, Object data) {
        super(HttpStatus.UNAUTHORIZED, code, message, data);
    }
}
