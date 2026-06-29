package com.example.prediction;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Live game snapshot used by prediction detail polling.")
public class GameLiveSnapshotDto {
    private String gameId;
    private String gameStatus;
    private Integer homeScore;
    private Integer awayScore;
    private Integer currentInning;
    private String currentInningHalf;
    private Integer lastEventSeq;
    private LocalDateTime lastUpdatedAt;
    private List<GameLiveEventDto> events;
    @Schema(description = "Normalized meaningful inning scores from game_inning_scores or derived from cumulative game_events scores. Older clients should tolerate this field being absent.")
    private List<GameInningScoreDto> inningScores;
}
