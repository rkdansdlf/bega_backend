package com.example.common.exception;

public class RepostTargetNotFoundException extends RuntimeException {
    private final String errorCode;

    public RepostTargetNotFoundException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public RepostTargetNotFoundException(String message) {
        this(null, message);
    }

    public String getErrorCode() {
        return errorCode;
    }
}
