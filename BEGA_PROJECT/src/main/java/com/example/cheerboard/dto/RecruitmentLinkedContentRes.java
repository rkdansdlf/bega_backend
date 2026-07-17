package com.example.cheerboard.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;

public record RecruitmentLinkedContentRes(
        Long partyId,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate gameDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
        @Schema(type = "string", format = "time", example = "18:30:00")
        LocalTime gameTime,
        String homeTeam,
        String awayTeam,
        String stadium,
        String section,
        Integer currentParticipants,
        Integer maxParticipants,
        String status,
        boolean recruiting,
        String description,
        Integer price,
        Integer ticketPrice,
        Integer reservationDepositAmount) {
}
