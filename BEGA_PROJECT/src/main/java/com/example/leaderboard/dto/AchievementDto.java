package com.example.leaderboard.dto;

import com.example.leaderboard.entity.Achievement;
import com.example.leaderboard.entity.UserAchievement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 업적 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AchievementDto {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String iconUrl;
    private String rarity;
    private String rarityKo;
    private String rarityColor;
    private Long pointsRequired;
    private Boolean earned;
    private LocalDateTime earnedAt;

    public static AchievementDto from(Achievement achievement, boolean earned, LocalDateTime earnedAt) {
        return AchievementDto.builder()
                .id(achievement.getId())
                .code(achievement.getCode())
                .name(achievement.getNameKo())
                .description(achievement.getDescriptionKo())
                .iconUrl(achievement.getIconUrl())
                .rarity(achievement.getRarity().name())
                .rarityKo(achievement.getRarity().getKoreanName())
                .rarityColor(achievement.getRarity().getColor())
                .pointsRequired(achievement.getPointsRequired())
                .earned(earned)
                .earnedAt(earnedAt)
                .build();
    }

    public static AchievementDto from(Achievement achievement) {
        return from(achievement, false, null);
    }

    public static AchievementDto from(UserAchievement userAchievement) {
        return from(userAchievement.getAchievement(), true, userAchievement.getEarnedAt());
    }
}
