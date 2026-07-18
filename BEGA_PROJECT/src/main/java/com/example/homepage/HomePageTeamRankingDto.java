package com.example.homepage;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomePageTeamRankingDto {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private Integer rank;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private String teamId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private String teamName;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private Integer wins;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private Integer losses;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private Integer draws;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private String winRate;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private Integer games;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private Double gamesBehind;
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true, description = "최근 5경기 결과, 최신순 (W/L/D), 데이터 없으면 null 또는 빈 리스트")
    private List<String> recentForm;
}
