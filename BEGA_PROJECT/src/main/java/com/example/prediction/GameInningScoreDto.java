package com.example.prediction;

import com.example.kbo.entity.GameInningScoreEntity;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GameInningScoreDto {
    private Integer inning;
    private String teamSide;
    private String teamCode;
    private Integer runs;
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
                .isExtra(score.getIsExtra())
                .build();
    }
}
