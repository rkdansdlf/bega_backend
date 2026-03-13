package com.example.kbo.exception;

import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;

public class TicketAnalysisException extends RuntimeException {
    private final HttpStatus status;

    public TicketAnalysisException(@NonNull HttpStatus status, @NonNull String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
