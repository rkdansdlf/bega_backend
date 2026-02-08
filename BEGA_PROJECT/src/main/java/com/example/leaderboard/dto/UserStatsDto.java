package com.example.leaderboard.dto;

import com.example.leaderboard.entity.UserScore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 사용자 통계 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatsDto {
    private Long userId;
    private String userName;
    private String profileImageUrl;
    private Long rank;

    // Scores
    private Long totalScore;
    private Long seasonScore;
    private Long monthlyScore;
    private Long weeklyScore;

    // Rankings
    private Long totalRank;
    private Long seasonRank;
    private Long monthlyRank;
    private Long weeklyRank;

    // Level & XP
    private Integer level;
    private String rankTitle;
    private Long experiencePoints;
    private Long nextLevelExp;
    private Double levelProgress;

    // Streaks
    private Integer currentStreak;
    private Integer maxStreak;

    // Stats
    private Integer correctPredictions;
    private Integer totalPredictions;
    private Double accuracy;

    // Achievements
    private Integer achievementCount;
    private List<AchievementDto> recentAchievements;

    // Powerups
    private List<PowerupInventoryDto> powerups;

    public static UserStatsDto from(UserScore userScore, String nickname, String profileImageUrl) {
        long nextLevelExp = userScore.getNextLevelExp();
        long currentExp = userScore.getExperiencePoints() != null ? userScore.getExperiencePoints() : 0L;
        double levelProgress = nextLevelExp > 0
                ? (double) currentExp / nextLevelExp * 100
                : 0.0;

        return UserStatsDto.builder()
                .userId(userScore.getUserId())
                .userName(nickname)
                .profileImageUrl(profileImageUrl)
                .totalScore(userScore.getTotalScore())
                .seasonScore(userScore.getSeasonScore())
                .monthlyScore(userScore.getMonthlyScore())
                .weeklyScore(userScore.getWeeklyScore())
                .level(userScore.getUserLevel())
                .rankTitle(userScore.getRankTier().name())
                .experiencePoints(userScore.getExperiencePoints())
                .nextLevelExp(nextLevelExp)
                .levelProgress(Math.round(levelProgress * 10) / 10.0)
                .currentStreak(userScore.getCurrentStreak())
                .maxStreak(userScore.getMaxStreak())
                .correctPredictions(userScore.getCorrectPredictions())
                .totalPredictions(userScore.getTotalPredictions())
                .accuracy(userScore.getAccuracy())
                .build();
    }
}
