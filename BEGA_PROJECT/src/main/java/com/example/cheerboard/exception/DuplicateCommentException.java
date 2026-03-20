package com.example.cheerboard.exception;

public class DuplicateCommentException extends RuntimeException {

    public DuplicateCommentException(String message) {
        super(message);
    }
}
