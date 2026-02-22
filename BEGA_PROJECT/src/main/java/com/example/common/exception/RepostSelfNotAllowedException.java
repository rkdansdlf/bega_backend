package com.example.common.exception;

public class RepostSelfNotAllowedException extends RepostNotAllowedException {
    public RepostSelfNotAllowedException(String message) {
        super(message);
    }

    public RepostSelfNotAllowedException(String errorCode, String message) {
        super(errorCode, message);
    }
}
