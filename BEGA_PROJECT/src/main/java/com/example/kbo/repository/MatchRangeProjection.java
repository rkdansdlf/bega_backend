package com.example.kbo.repository;

import java.time.LocalDate;
import java.time.LocalTime;

public interface MatchRangeProjection {

    String getGameId();

    LocalDate getGameDate();

    String getStadium();

    String getHomeTeam();

    String getAwayTeam();

    Integer getHomeScore();

    Integer getAwayScore();

    Boolean getIsDummy();

    String getHomePitcher();

    String getAwayPitcher();

    Integer getSeasonId();

    Integer getRawLeagueTypeCode();

    Integer getSeriesGameNo();

    String getGameStatus();

    LocalTime getStartTime();
}
