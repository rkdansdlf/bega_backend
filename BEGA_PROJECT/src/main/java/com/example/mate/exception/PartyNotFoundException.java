package com.example.mate.exception;

import com.example.common.exception.NotFoundBusinessException;

public class PartyNotFoundException extends NotFoundBusinessException {
    public PartyNotFoundException(Long partyId) {
        super("PARTY_NOT_FOUND", "파티를 찾을 수 없습니다. ID: " + partyId);
    }
    
    public PartyNotFoundException(String message) {
        super("PARTY_NOT_FOUND", message);
    }
}
