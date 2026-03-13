package com.example.mate.exception;

import com.example.common.exception.BadRequestBusinessException;

public class InvalidApplicationStatusException extends BadRequestBusinessException {
    public InvalidApplicationStatusException(String message) {
        super("INVALID_APPLICATION_STATUS", message);
    }
}
