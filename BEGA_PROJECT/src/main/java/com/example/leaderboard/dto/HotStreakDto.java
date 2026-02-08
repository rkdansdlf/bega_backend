package com.example.leaderboard.dto;

import com.example.leaderboard.entity.UserScore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 핫 스트릭 (연승 중인 유저) DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotStreakDto {
    private Long userId;
    private String userName;
    private String profileImageUrl;
    private Integer streak;
    private Integer level;
    private String rankTier;
    private Long totalScore;

    public static HotStreakDto from(UserScore userScore, String nickname, String profileImageUrl) {
        return HotStreakDto.builder()
                .userId(userScore.getUserId())
                .userName(nickname)
                .profileImageUrl(profileImageUrl)
                .streak(userScore.getCurrentStreak())
                .level(userScore.getUserLevel())
                .rankTier(userScore.getRankTier().name())
                .totalScore(userScore.getTotalScore())
                .build();
    }
}
