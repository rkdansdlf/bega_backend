package com.example.common.exception;

public class AuthenticationRequiredException extends UnauthorizedBusinessException {

    public AuthenticationRequiredException(String message) {
        super("AUTHENTICATION_REQUIRED", message);
    }
}
