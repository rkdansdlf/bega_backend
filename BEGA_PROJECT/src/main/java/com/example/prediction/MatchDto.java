package com.example.prediction;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.LocalTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class MatchDto {
    private String gameId;
    private LocalDate gameDate;
    private String homeTeam;
    private String awayTeam;
    private String stadium;
    private LocalTime startTime;
    private Integer homeScore;
    private Integer awayScore;
    private String winner;
    private String gameStatus;
    @JsonProperty("isDummy")
    private Boolean isDummy;

    // New Fields
    private PitcherDto homePitcher;
    private PitcherDto awayPitcher;
    private String aiSummary;
    private WinProbabilityDto winProbability;

    // Season Context
    private Integer seasonId;
    private String leagueType; // REGULAR, POST, PRE
    private String postSeasonSeries; // WC, DS, PO, KS
    private Integer seriesGameNo;

    @Getter
    @Builder
    public static class PitcherDto {
        private String name;
        private String era;
        private Integer win;
        private Integer loss;
        private String imgUrl;
    }

    @Getter
    @Builder
    public static class WinProbabilityDto {
        private Double home;
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
