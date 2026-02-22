package com.example.common.exception;

public class IdentityVerificationRequiredException extends RuntimeException {
    public IdentityVerificationRequiredException(String message) {
        super(message);
    }
}
