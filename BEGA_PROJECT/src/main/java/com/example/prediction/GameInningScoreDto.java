package com.example.prediction;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.example.kbo.entity.GameInningScoreEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GameInningScoreDto {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private Integer inning;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private String teamSide;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private String teamCode;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private Integer runs;
    @JsonProperty("isExtra")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private Boolean isExtra;

    public static GameInningScoreDto fromEntity(GameInningScoreEntity score) {
        if (score == null) {
            return null;
        }
        return GameInningScoreDto.builder()
                .inning(score.getInning())
                .teamSide(score.getTeamSide())
                .teamCode(score.getTeamCode())
                .runs(score.getRuns())
                .isExtra(GameInningScoreSupport.normalizeIsExtraFlag(score.getInning(), score.getIsExtra()))
                .build();
    }
}
