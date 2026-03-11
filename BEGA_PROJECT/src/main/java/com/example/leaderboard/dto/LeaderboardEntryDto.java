package com.example.leaderboard.dto;

import com.example.leaderboard.entity.UserScore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 리더보드 엔트리 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardEntryDto {
    private Long rank;
    private String handle;
    private String userName;
    private String profileImageUrl;
    private Long score;
    private Integer level;
    private String rankTitle;
    private Integer streak;
    private Integer maxStreak;
    private Double accuracy;

    public static LeaderboardEntryDto from(UserScore userScore, Long rank, String handle, String nickname, String profileImageUrl) {
        return LeaderboardEntryDto.builder()
                .rank(rank)
                .handle(handle)
                .userName(nickname)
                .profileImageUrl(profileImageUrl)
                .score(userScore.getTotalScore())
                .level(userScore.getUserLevel())
                .rankTitle(userScore.getRankTier().name())
                .streak(userScore.getCurrentStreak())
                .maxStreak(userScore.getMaxStreak())
                .accuracy(userScore.getAccuracy())
                .build();
    }

    public static LeaderboardEntryDto fromWithScore(
            UserScore userScore,
            Long rank,
            Long score,
            String handle,
            String nickname,
            String profileImageUrl) {
        return LeaderboardEntryDto.builder()
                .rank(rank)
                .handle(handle)
                .userName(nickname)
                .profileImageUrl(profileImageUrl)
                .score(score)
                .level(userScore.getUserLevel())
                .rankTitle(userScore.getRankTier().name())
                .streak(userScore.getCurrentStreak())
                .maxStreak(userScore.getMaxStreak())
                .accuracy(userScore.getAccuracy())
                .build();
    }
}
