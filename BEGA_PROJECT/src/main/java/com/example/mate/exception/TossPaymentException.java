package com.example.mate.exception;

import org.springframework.http.HttpStatusCode;

public class TossPaymentException extends RuntimeException {

    private final HttpStatusCode statusCode;
    private final String tossErrorCode;

    public TossPaymentException(String message, HttpStatusCode statusCode) {
        super(message);
        this.statusCode = statusCode;
        this.tossErrorCode = null;
    }

    public TossPaymentException(String message, HttpStatusCode statusCode, String tossErrorCode) {
        super(message);
        this.statusCode = statusCode;
        this.tossErrorCode = tossErrorCode;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }

    public String getTossErrorCode() {
        return tossErrorCode;
    }
}
