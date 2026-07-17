package com.example.cheerboard.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

public record CheckinLinkedContentRes(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate gameDate,
        String homeTeam,
        String awayTeam,
        String cheeringTeam,
        String stadium,
        boolean verified) {
}
