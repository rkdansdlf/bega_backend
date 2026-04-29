package com.example.prediction;

import java.time.LocalDateTime;

import com.example.kbo.entity.GameEventEntity;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GameLiveEventDto {
    private Integer eventSeq;
    private Integer inning;
    private String inningHalf;
    private Integer outs;
    private String batterName;
    private String pitcherName;
    private String description;
    private String eventType;
    private String resultCode;
    private Integer rbi;
    private String basesBefore;
    private String basesAfter;
    private Integer homeScore;
    private Integer awayScore;
    private Double wpa;
    private Double winExpectancyBefore;
    private Double winExpectancyAfter;
    private LocalDateTime updatedAt;

    public static GameLiveEventDto fromEntity(GameEventEntity event) {
        if (event == null) {
            return null;
        }
        return GameLiveEventDto.builder()
                .eventSeq(event.getEventSeq())
                .inning(event.getInning())
                .inningHalf(event.getInningHalf())
                .outs(event.getOuts())
                .batterName(event.getBatterName())
                .pitcherName(event.getPitcherName())
                .description(event.getDescription())
                .eventType(event.getEventType())
                .resultCode(event.getResultCode())
                .rbi(event.getRbi())
                .basesBefore(event.getBasesBefore())
                .basesAfter(event.getBasesAfter())
                .homeScore(event.getHomeScore())
                .awayScore(event.getAwayScore())
                .wpa(event.getWpa())
                .winExpectancyBefore(event.getWinExpectancyBefore())
                .winExpectancyAfter(event.getWinExpectancyAfter())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}
