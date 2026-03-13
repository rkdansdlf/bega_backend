package com.example.mate.exception;

import com.example.common.exception.ForbiddenBusinessException;

public class UnauthorizedAccessException extends ForbiddenBusinessException {
    public UnauthorizedAccessException(String message) {
        super("FORBIDDEN", message);
    }
}
