package com.example.prediction;

import com.example.kbo.entity.GameSummaryEntity;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GameSummaryDto {
    private String type;
    private Integer playerId;
    private String playerName;
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
