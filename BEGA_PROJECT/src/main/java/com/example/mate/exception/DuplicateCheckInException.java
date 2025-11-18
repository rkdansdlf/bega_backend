package com.example.mate.exception;

public class DuplicateCheckInException extends RuntimeException {
    public DuplicateCheckInException(Long partyId, Long userId) {
        super(String.format("이미 체크인하셨습니다. (파티 ID: %d, 사용자 ID: %d)", partyId, userId));
    }
}