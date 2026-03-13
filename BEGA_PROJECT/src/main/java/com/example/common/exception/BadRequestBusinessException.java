package com.example.common.exception;

import org.springframework.http.HttpStatus;

public class BadRequestBusinessException extends BusinessException {

    public BadRequestBusinessException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }

    public BadRequestBusinessException(String code, String message, Object data) {
        super(HttpStatus.BAD_REQUEST, code, message, data);
    }
}
