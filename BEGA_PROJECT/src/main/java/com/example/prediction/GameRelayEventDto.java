package com.example.prediction;

import java.time.LocalDateTime;

import com.example.kbo.entity.GamePlayByPlayEntity;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GameRelayEventDto {

    private Integer relayId;
    private Integer inning;
    private String inningHalf;
    private String pitcherName;
    private String batterName;
    private String playDescription;
    private String eventType;
    private String result;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static GameRelayEventDto fromEntity(GamePlayByPlayEntity event) {
        if (event == null) {
            return null;
        }
        return GameRelayEventDto.builder()
                .relayId(event.getId())
                .inning(event.getInning())
                .inningHalf(event.getInningHalf())
                .pitcherName(event.getPitcherName())
                .batterName(event.getBatterName())
                .playDescription(event.getPlayDescription())
                .eventType(event.getEventType())
                .result(event.getResult())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}
