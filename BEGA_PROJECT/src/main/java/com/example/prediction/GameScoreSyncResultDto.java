package com.example.prediction;

public record GameScoreSyncResultDto(
        String gameId,
        Integer homeScore,
        Integer awayScore,
        String gameStatus,
        int inningScoreCount,
        boolean synced,
        boolean usedInningScores,
        String winningTeam,
        Integer winningScore
) {
}
