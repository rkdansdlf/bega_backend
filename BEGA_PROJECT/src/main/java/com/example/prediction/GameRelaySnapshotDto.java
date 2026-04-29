package com.example.prediction;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GameRelaySnapshotDto {

    private String gameId;
    private Integer lastRelayId;
    private LocalDateTime lastUpdatedAt;
    private List<GameRelayEventDto> events;
}
