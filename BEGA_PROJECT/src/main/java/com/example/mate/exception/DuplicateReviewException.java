package com.example.mate.exception;

import com.example.common.exception.ConflictBusinessException;

public class DuplicateReviewException extends ConflictBusinessException {
    public DuplicateReviewException(Long partyId, Long reviewerId, Long revieweeId) {
        super("DUPLICATE_REVIEW", "이미 리뷰를 작성했습니다. PartyId: " + partyId +
              ", ReviewerId: " + reviewerId + ", RevieweeId: " + revieweeId);
    }
}
