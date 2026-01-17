package com.example.common.exception;

public class TeamNotFoundException extends RuntimeException {
    public TeamNotFoundException(String teamId) {
        super("존재하지 않는 팀 ID입니다: " + teamId);
    }
}
