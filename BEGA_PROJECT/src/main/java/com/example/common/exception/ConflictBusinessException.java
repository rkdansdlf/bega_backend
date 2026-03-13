package com.example.common.exception;

import org.springframework.http.HttpStatus;

public class ConflictBusinessException extends BusinessException {

    public ConflictBusinessException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }

    public ConflictBusinessException(String code, String message, Object data) {
        super(HttpStatus.CONFLICT, code, message, data);
    }
}
