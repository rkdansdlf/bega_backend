package com.example.BegaDiary.Exception;

public class GameNotFoundException extends RuntimeException {
    
    public GameNotFoundException() {
        super("경기(Game) 정보를 찾을 수 없습니다.");
    }
    
    public GameNotFoundException(Long gameId) {
        super("해당 경기 ID(" + gameId + ")를 찾을 수 없습니다.");
    }
}