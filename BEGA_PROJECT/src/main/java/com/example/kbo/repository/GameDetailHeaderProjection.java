package com.example.kbo.repository;

import java.time.LocalDate;
import java.time.LocalTime;

public interface GameDetailHeaderProjection {

    String getGameId();

    LocalDate getGameDate();

    String getStadium();

    String getStadiumName();

    LocalTime getStartTime();

    Integer getAttendance();

    String getWeather();

    Integer getGameTimeMinutes();

    String getHomeTeam();

    String getAwayTeam();

    Integer getHomeScore();

    Integer getAwayScore();

    String getHomePitcher();

    String getAwayPitcher();

    String getGameStatus();
}
