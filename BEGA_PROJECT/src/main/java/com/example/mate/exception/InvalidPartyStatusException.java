package com.example.mate.exception;

public class InvalidPartyStatusException extends RuntimeException {
    public InvalidPartyStatusException(String status) {
        super("잘못된 파티 상태입니다: " + status);
    }
}