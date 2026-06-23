package com.example.prediction;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class MatchDto {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String gameId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate gameDate;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String homeTeam;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String awayTeam;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private String stadium;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private LocalTime startTime;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private Integer homeScore;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private Integer awayScore;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private String winner;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private String gameStatus;
    @JsonProperty("isDummy")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private Boolean isDummy;

    // New Fields
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private PitcherDto homePitcher;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private PitcherDto awayPitcher;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private String aiSummary;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private WinProbabilityDto winProbability;

    // Season Context
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private Integer seasonId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private String leagueType; // REGULAR, POST, PRE
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private String postSeasonSeries; // WC, DS, PO, KS
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private Integer seriesGameNo;

    @Getter
    @Builder
    public static class PitcherDto {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        private String name;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        private String era;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        private Integer win;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        private Integer loss;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        private String imgUrl;
    }

    @Getter
    @Builder
    public static class WinProbabilityDto {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        private Double home;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        private Double away;
    }

    public static PitcherDto pitcherOf(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return PitcherDto.builder()
                .name(name)
                .era(null)
                .win(null)
                .loss(null)
                .imgUrl(null)
                .build();
    }
}
