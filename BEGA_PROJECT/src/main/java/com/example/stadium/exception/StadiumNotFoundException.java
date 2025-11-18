package com.example.stadium.exception;

public class StadiumNotFoundException extends RuntimeException {
    public StadiumNotFoundException(String stadiumId) {
        super("경기장을 찾을 수 없습니다. ID: " + stadiumId);
    }
    
    public StadiumNotFoundException(String field, String value) {
        super(String.format("경기장을 찾을 수 없습니다. %s: %s", field, value));
    }
}