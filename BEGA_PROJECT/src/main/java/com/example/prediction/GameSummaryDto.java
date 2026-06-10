package com.example.prediction;

import com.example.kbo.entity.GameSummaryEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GameSummaryDto {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private String type;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private Integer playerId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private String playerName;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    private String detail;

    public static GameSummaryDto fromEntity(GameSummaryEntity summary) {
        if (summary == null) {
            return null;
        }
        return GameSummaryDto.builder()
                .type(summary.getSummaryType())
                .playerId(summary.getPlayerId())
                .playerName(summary.getPlayerName())
                .detail(summary.getDetailText())
                .build();
    }
}
