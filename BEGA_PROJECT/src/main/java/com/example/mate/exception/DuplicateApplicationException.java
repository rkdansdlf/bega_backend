package com.example.mate.exception;

import com.example.common.exception.ConflictBusinessException;

public class DuplicateApplicationException extends ConflictBusinessException {
    public DuplicateApplicationException(Long partyId, Long applicantId) {
        super("DUPLICATE_APPLICATION",
                String.format("이미 신청한 파티입니다. (파티 ID: %d, 신청자 ID: %d)", partyId, applicantId));
    }
    
    public DuplicateApplicationException(String message) {
        super("DUPLICATE_APPLICATION", message);
    }
}
