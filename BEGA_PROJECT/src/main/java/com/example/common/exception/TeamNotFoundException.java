package com.example.common.exception;

public class TeamNotFoundException extends NotFoundBusinessException {
    public TeamNotFoundException(String teamId) {
        super("TEAM_NOT_FOUND", "존재하지 않는 팀 ID입니다: " + teamId);
    }
}
