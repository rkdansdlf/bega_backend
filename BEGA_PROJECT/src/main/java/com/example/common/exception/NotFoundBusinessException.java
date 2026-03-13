package com.example.common.exception;

import org.springframework.http.HttpStatus;

public class NotFoundBusinessException extends BusinessException {

    public NotFoundBusinessException(String code, String message) {
        super(HttpStatus.NOT_FOUND, code, message);
    }

    public NotFoundBusinessException(String code, String message, Object data) {
        super(HttpStatus.NOT_FOUND, code, message, data);
    }
}
