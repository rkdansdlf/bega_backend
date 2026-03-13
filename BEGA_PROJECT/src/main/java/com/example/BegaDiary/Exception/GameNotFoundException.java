package com.example.BegaDiary.Exception;

import com.example.common.exception.NotFoundBusinessException;

public class GameNotFoundException extends NotFoundBusinessException {
    
    public GameNotFoundException() {
        super("GAME_NOT_FOUND", "경기(Game) 정보를 찾을 수 없습니다.");
    }
    
    public GameNotFoundException(Long gameId) {
        super("GAME_NOT_FOUND", "해당 경기 ID(" + gameId + ")를 찾을 수 없습니다.");
    }
}
