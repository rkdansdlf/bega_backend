package com.example.mate.exception;

import com.example.common.exception.BadRequestBusinessException;

public class InvalidReviewException extends BadRequestBusinessException {
    public InvalidReviewException(String message) {
        super("INVALID_REVIEW", message);
    }
}
