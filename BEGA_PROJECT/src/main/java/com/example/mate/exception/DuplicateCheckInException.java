package com.example.mate.exception;

import com.example.common.exception.ConflictBusinessException;

public class DuplicateCheckInException extends ConflictBusinessException {
    public DuplicateCheckInException(Long partyId, Long userId) {
        super("DUPLICATE_CHECK_IN",
                String.format("이미 체크인하셨습니다. (파티 ID: %d, 사용자 ID: %d)", partyId, userId));
    }
}
