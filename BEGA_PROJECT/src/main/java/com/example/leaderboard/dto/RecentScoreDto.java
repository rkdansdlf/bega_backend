package com.example.leaderboard.dto;

import com.example.leaderboard.entity.ScoreEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 최근 점수 획득 이벤트 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentScoreDto {
    private Long id;
    private String handle;
    private String userName;
    private String profileImageUrl;
    private String eventType;
    private String eventTypeKo;
    private Integer baseScore;
    private Double multiplier;
    private Integer score;
    private Integer streak;
    private String description;
    private LocalDateTime timestamp;

    public static RecentScoreDto from(ScoreEvent event, String handle, String nickname, String profileImageUrl) {
        return RecentScoreDto.builder()
                .id(event.getId())
                .handle(handle)
                .userName(nickname)
                .profileImageUrl(profileImageUrl)
                .eventType(event.getEventType().name())
                .eventTypeKo(event.getEventType().getKoreanName())
                .baseScore(event.getBaseScore())
                .multiplier(event.getMultiplier() == null ? null : event.getMultiplier().doubleValue())
                .score(event.getFinalScore())
                .streak(event.getStreakCount())
                .description(event.getDescription())
                .timestamp(event.getCreatedAt())
                .build();
    }
}
