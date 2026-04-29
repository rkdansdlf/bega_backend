package com.example.prediction;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GameLiveSummaryDto {
    private String gameId;
    private String gameStatus;
    private Integer homeScore;
    private Integer awayScore;
    private Integer lastEventSeq;
    private LocalDateTime lastUpdatedAt;
}
