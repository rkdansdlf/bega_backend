package com.example.common.exception;

public class InvalidAuthorException extends UnauthorizedBusinessException {
    public InvalidAuthorException(String message) {
        super("INVALID_AUTHOR", message);
    }
}
