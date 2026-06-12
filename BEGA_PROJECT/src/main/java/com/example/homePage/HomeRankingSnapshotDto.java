package com.example.homepage;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AccessLevel;
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
    @Getter(AccessLevel.NONE)
    @JsonAlias("offSeason")
    @JsonProperty("isOffSeason")
    private boolean isOffSeason;
    private List<HomePageTeamRankingDto> rankings;

    @JsonProperty("isOffSeason")
    public boolean isOffSeason() {
        return isOffSeason;
    }
}
