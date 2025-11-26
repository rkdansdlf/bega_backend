package com.example.mate.exception;

public class DuplicateApplicationException extends RuntimeException {
    public DuplicateApplicationException(Long partyId, Long applicantId) {
        super(String.format("이미 신청한 파티입니다. (파티 ID: %d, 신청자 ID: %d)", partyId, applicantId));
    }
    
    public DuplicateApplicationException(String message) {
        super(message);
    }
}