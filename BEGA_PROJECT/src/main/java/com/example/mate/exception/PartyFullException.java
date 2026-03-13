package com.example.mate.exception;

import com.example.common.exception.BadRequestBusinessException;

public class PartyFullException extends BadRequestBusinessException {
    public PartyFullException(Long partyId) {
        super("PARTY_FULL", "파티가 이미 가득 찼습니다. ID: " + partyId);
    }
    
    public PartyFullException(String message) {
        super("PARTY_FULL", message);
    }
}
