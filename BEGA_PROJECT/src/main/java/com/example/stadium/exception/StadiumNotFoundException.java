package com.example.stadium.exception;

import com.example.common.exception.NotFoundBusinessException;

public class StadiumNotFoundException extends NotFoundBusinessException {
    public StadiumNotFoundException(String stadiumId) {
        super("STADIUM_NOT_FOUND", "경기장을 찾을 수 없습니다. ID: " + stadiumId);
    }
    
    public StadiumNotFoundException(String field, String value) {
        super("STADIUM_NOT_FOUND", String.format("경기장을 찾을 수 없습니다. %s: %s", field, value));
    }
}
