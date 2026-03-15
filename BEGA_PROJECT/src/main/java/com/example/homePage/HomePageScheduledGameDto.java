package com.example.homepage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomePageScheduledGameDto {

    private String gameId;
    private String time;
    private String stadium;
    private String gameStatus;
    private String gameStatusKr;
    private String gameInfo;
    private String leagueType;
    private String homeTeam;
    private String homeTeamFull;
    private String awayTeam;
    private String awayTeamFull;
    private Integer homeScore;
    private Integer awayScore;
    private String sourceDate;
    private String leagueBadge;
}
