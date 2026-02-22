package com.example.leaderboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 점수 계산 결과 DTO
 * 예측 결과 처리 후 반환되는 정보
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreResultDto {
    private Long userId;
    private Boolean correct;

    // Score breakdown
    private Integer baseScore;
    private Double multiplier;
    private Integer bonusScore;
    private Integer totalEarned;

    // Updated stats
    private Long newTotalScore;
    private Integer newLevel;
    private String newRankTier;
    private Long experiencePoints;

    // Streak info
    private Integer currentStreak;
    private Boolean isNewMaxStreak;

    // Applied powerups
    private List<String> appliedPowerups;

    // Unlocked achievements
    private List<AchievementDto> unlockedAchievements;

    // Level up info
    private Boolean leveledUp;
    private Integer previousLevel;

    public static ScoreResultDto incorrect(Long userId, int currentStreak) {
        return ScoreResultDto.builder()
                .userId(userId)
                .correct(false)
                .baseScore(0)
                .multiplier(1.0)
                .bonusScore(0)
                .totalEarned(0)
                .currentStreak(0)
                .isNewMaxStreak(false)
                .leveledUp(false)
                .build();
    }
}
