package com.example.demo.exception;

public class IdentityVerificationRequiredException extends RuntimeException {
    public IdentityVerificationRequiredException(String message) {
        super(message);
    }
}
