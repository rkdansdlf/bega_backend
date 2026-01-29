package com.example.leaderboard.dto;

import com.example.leaderboard.entity.ScoreEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 최근 점수 획득 이벤트 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentScoreDto {
    private Long id;
    private Long userId;
    private String userName;
    private String profileImageUrl;
    private String eventType;
    private String eventTypeKo;
    private Integer baseScore;
    private Double multiplier;
    private Integer score;
    private Integer streak;
    private String description;
    private Instant timestamp;

    public static RecentScoreDto from(ScoreEvent event, String nickname, String profileImageUrl) {
        return RecentScoreDto.builder()
                .id(event.getId())
                .userId(event.getUserId())
                .userName(nickname)
                .profileImageUrl(profileImageUrl)
                .eventType(event.getEventType().name())
                .eventTypeKo(event.getEventType().getKoreanName())
                .baseScore(event.getBaseScore())
                .multiplier(event.getMultiplier())
                .score(event.getFinalScore())
                .streak(event.getStreakCount())
                .description(event.getDescription())
                .timestamp(event.getCreatedAt())
                .build();
    }
}
