package com.example.mate.exception;

public class PartyApplicationNotFoundException extends RuntimeException {
    public PartyApplicationNotFoundException(Long applicationId) {
        super("신청을 찾을 수 없습니다. ID: " + applicationId);
    }
}