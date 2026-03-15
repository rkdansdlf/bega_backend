package com.example.homepage;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeBootstrapResponseDto {

    private String selectedDate;
    private LeagueStartDatesDto leagueStartDates;
    private HomeScheduleNavigationDto navigation;
    private List<HomePageGameDto> games;
    private List<HomePageScheduledGameDto> scheduledGamesWindow;
    private Integer rankingSeasonYear;
    private String rankingSourceMessage;
    @JsonProperty("isOffSeason")
    private boolean isOffSeason;
    private List<HomePageTeamRankingDto> rankings;
}
