package com.example.homepage;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeRankingSnapshotDto {

    private Integer rankingSeasonYear;
    private String rankingSourceMessage;
    private boolean isOffSeason;
    private List<HomePageTeamRankingDto> rankings;
}
