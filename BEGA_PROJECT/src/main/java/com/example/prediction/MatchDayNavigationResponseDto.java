package com.example.prediction;

import java.time.LocalDate;
import java.util.List;

import lombok.Getter;

@Getter
public class MatchDayNavigationResponseDto {
    private final LocalDate date;
    private final List<MatchDto> games;
    private final LocalDate prevDate;
    private final LocalDate nextDate;
    private final boolean hasPrev;
    private final boolean hasNext;

    public MatchDayNavigationResponseDto(
            LocalDate date,
            List<MatchDto> games,
            LocalDate prevDate,
            LocalDate nextDate,
            boolean hasPrev,
            boolean hasNext
    ) {
        this.date = date;
        this.games = games;
        this.prevDate = prevDate;
        this.nextDate = nextDate;
        this.hasPrev = hasPrev;
        this.hasNext = hasNext;
    }
}
