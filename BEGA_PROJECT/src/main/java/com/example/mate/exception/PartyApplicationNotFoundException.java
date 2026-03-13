package com.example.mate.exception;

import com.example.common.exception.NotFoundBusinessException;

public class PartyApplicationNotFoundException extends NotFoundBusinessException {
    public PartyApplicationNotFoundException(Long applicationId) {
        super("PARTY_APPLICATION_NOT_FOUND", "신청을 찾을 수 없습니다. ID: " + applicationId);
    }
}
