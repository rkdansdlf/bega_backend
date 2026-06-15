package com.example.homepage;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
}
