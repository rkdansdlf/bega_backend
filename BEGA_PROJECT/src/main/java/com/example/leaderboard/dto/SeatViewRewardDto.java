package com.example.leaderboard.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 좌석 시야 사진 기여 리워드 결과 DTO
 */
@Getter
@Builder
public class SeatViewRewardDto {

    /** 획득한 포인트 */
    private int pointsEarned;

    /** 첫 번째 시야 사진 기여 여부 */
    private boolean firstContribution;

    /** 이번 기여로 획득한 업적 목록 */
    private List<AchievementDto> unlockedAchievements;

    /** 총 시야 사진 기여 횟수 */
    private long totalContributions;
}
