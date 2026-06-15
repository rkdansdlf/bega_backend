package com.example.homepage;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private Integer rankingSeasonYear;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private String rankingSourceMessage;
    @Getter(AccessLevel.NONE)
    @JsonAlias("offSeason")
    @JsonProperty("isOffSeason")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean isOffSeason;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private List<HomePageTeamRankingDto> rankings;

    @JsonProperty("isOffSeason")
    public boolean isOffSeason() {
        return isOffSeason;
    }
}
