package com.example.BegaDiary.Exception;

import com.example.common.exception.BadRequestBusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
public class WinningNameNotFoundException extends BadRequestBusinessException {
    public WinningNameNotFoundException() {
        super("WINNING_NAME_NOT_FOUND", "응원 팀 승패가 선택되지 않았습니다.");
    }
}
