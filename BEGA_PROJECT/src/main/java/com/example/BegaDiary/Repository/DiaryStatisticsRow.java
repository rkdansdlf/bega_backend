package com.example.BegaDiary.Repository;

import java.time.LocalDate;

import com.example.BegaDiary.Entity.BegaDiary.DiaryEmoji;
import com.example.BegaDiary.Entity.BegaDiary.DiaryType;
import com.example.BegaDiary.Entity.BegaDiary.DiaryWinning;

public interface DiaryStatisticsRow {

    LocalDate getDiaryDate();

    DiaryWinning getWinning();

    DiaryType getType();

    String getStadium();

    DiaryEmoji getMood();

    String getHomeTeam();

    String getAwayTeam();

    String getFavoriteTeamId();
}
