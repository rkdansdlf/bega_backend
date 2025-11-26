package com.example.mate.exception;

public class PartyNotFoundException extends RuntimeException {
    public PartyNotFoundException(Long partyId) {
        super("파티를 찾을 수 없습니다. ID: " + partyId);
    }
    
    public PartyNotFoundException(String message) {
        super(message);
    }
}