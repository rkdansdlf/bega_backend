package com.example.BegaDiary.Exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
public class WinningNameNotFoundException extends RuntimeException {
    public WinningNameNotFoundException() {
        super("응원 팀 승패가 선택되지 않았습니다.");
    }
}