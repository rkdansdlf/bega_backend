package com.example.prediction;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record NonCanonicalGameDto(
        String gameId,
        LocalDate gameDate,
        LocalTime startTime,
        String rawStatus,
        String homeTeam,
        String awayTeam,
        Integer homeScore,
        Integer awayScore,
        List<String> reasons
) {
}
