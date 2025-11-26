package com.example.mate.exception;

public class PartyFullException extends RuntimeException {
    public PartyFullException(Long partyId) {
        super("파티가 이미 가득 찼습니다. ID: " + partyId);
    }
    
    public PartyFullException(String message) {
        super(message);
    }
}