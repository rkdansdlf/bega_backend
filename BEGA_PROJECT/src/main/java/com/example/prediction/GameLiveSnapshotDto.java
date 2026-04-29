package com.example.prediction;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
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
}
