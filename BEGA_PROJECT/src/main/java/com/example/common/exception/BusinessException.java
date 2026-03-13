package com.example.common.exception;

import org.springframework.http.HttpStatus;

public abstract class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Object data;

    protected BusinessException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    protected BusinessException(HttpStatus status, String code, String message, Object data) {
        super(message);
        this.status = status;
        this.code = code;
        this.data = data;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Object getData() {
        return data;
    }
}
