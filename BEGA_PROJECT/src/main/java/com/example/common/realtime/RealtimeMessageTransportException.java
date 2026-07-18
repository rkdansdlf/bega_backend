package com.example.common.realtime;

public class RealtimeMessageTransportException extends RuntimeException {

    public RealtimeMessageTransportException(String message) {
        super(message);
    }

    public RealtimeMessageTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
