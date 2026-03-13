package com.example.common.exception;

import org.springframework.http.HttpStatus;

public class InternalServerBusinessException extends BusinessException {

    public InternalServerBusinessException(String code, String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, code, message);
    }

    public InternalServerBusinessException(String code, String message, Object data) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, code, message, data);
    }
}
