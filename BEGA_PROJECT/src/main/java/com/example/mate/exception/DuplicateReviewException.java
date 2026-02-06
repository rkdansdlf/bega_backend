package com.example.mate.exception;

public class DuplicateReviewException extends RuntimeException {
    public DuplicateReviewException(Long partyId, Long reviewerId, Long revieweeId) {
        super("이미 리뷰를 작성했습니다. PartyId: " + partyId +
              ", ReviewerId: " + reviewerId + ", RevieweeId: " + revieweeId);
    }
}
