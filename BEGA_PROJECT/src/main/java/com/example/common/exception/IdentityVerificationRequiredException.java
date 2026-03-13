package com.example.common.exception;

public class IdentityVerificationRequiredException extends ForbiddenBusinessException {
    public IdentityVerificationRequiredException(String message) {
        super("IDENTITY_VERIFICATION_REQUIRED", message);
    }
}
