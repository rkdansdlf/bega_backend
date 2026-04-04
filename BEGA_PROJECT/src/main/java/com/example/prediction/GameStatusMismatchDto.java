package com.example.prediction;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record GameStatusMismatchDto(
        String gameId,
        LocalDate gameDate,
        LocalTime startTime,
        String rawStatus,
        String normalizedRawStatus,
        String effectiveStatus,
        Integer homeScore,
        Integer awayScore,
        int inningScoreCount,
        boolean hasKnownScore,
        boolean hasInningScores,
        List<String> reasons
) {
}
