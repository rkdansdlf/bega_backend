package com.example.common.exception;

public class RepostNotAllowedException extends RuntimeException {
    private final String errorCode;

    public RepostNotAllowedException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public RepostNotAllowedException(String message) {
        this(null, message);
    }

    public String getErrorCode() {
        return errorCode;
    }
}
