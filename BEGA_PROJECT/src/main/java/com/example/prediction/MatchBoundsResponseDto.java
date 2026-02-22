package com.example.prediction;

import java.time.LocalDate;

import lombok.Getter;

@Getter
public class MatchBoundsResponseDto {
    private final LocalDate earliestGameDate;
    private final LocalDate latestGameDate;
    private final boolean hasData;

    public MatchBoundsResponseDto(LocalDate earliestGameDate, LocalDate latestGameDate, boolean hasData) {
        this.earliestGameDate = earliestGameDate;
        this.latestGameDate = latestGameDate;
        this.hasData = hasData;
    }
}
