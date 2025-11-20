package com.example.prediction;

import java.time.LocalDate;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Tolerate;

@Getter
@Builder(toBuilder = true)
public class MatchDto {
    private String gameId;
    private LocalDate gameDate;
    private String homeTeam;
    private String awayTeam;
    private String stadium;
    private Integer homeScore;
    private Integer awayScore;
    private String winner;

    @Tolerate
    public MatchDto() {}

    public static MatchDto fromEntity(Match match) {
        return MatchDto.builder()
                .gameId(match.getGameId())
                .gameDate(match.getGameDate())
                .homeTeam(match.getHomeTeam())
                .awayTeam(match.getAwayTeam())
                .stadium(match.getStadium())
                .homeScore(match.getHomeScore())
                .awayScore(match.getAwayScore())
                .winner(match.getWinner())
                .build();
    }
}