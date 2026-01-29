package com.example.leaderboard.service;

import com.example.leaderboard.dto.AchievementDto;
import com.example.leaderboard.entity.Achievement;
import com.example.leaderboard.entity.UserAchievement;
import com.example.leaderboard.entity.UserScore;
import com.example.leaderboard.repository.AchievementRepository;
import com.example.leaderboard.repository.UserAchievementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 업적 관리 서비스
 * 업적 확인 및 부여를 담당합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AchievementService {

    private final AchievementRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;

    /**
     * 사용자의 모든 업적 조회
     */
    @Transactional(readOnly = true)
    public List<AchievementDto> getUserAchievements(Long userId) {
        // 획득한 업적 코드 목록
        List<String> earnedCodes = userAchievementRepository.findEarnedAchievementCodes(userId);

        // 모든 업적 조회
        List<Achievement> allAchievements = achievementRepository.findAllOrderByRarityDesc();

        return allAchievements.stream()
                .map(achievement -> {
                    boolean earned = earnedCodes.contains(achievement.getCode());
                    if (earned) {
                        UserAchievement ua = userAchievementRepository
                                .findByUserIdAndAchievementCode(userId, achievement.getCode())
                                .orElse(null);
                        return AchievementDto.from(achievement, true, ua != null ? ua.getEarnedAt() : null);
                    }
                    return AchievementDto.from(achievement, false, null);
                })
                .toList();
    }

    /**
     * 최근 획득한 업적 조회
     */
    @Transactional(readOnly = true)
    public List<AchievementDto> getRecentAchievements(Long userId, int limit) {
        return userAchievementRepository.findRecentByUserId(userId, PageRequest.of(0, limit)).stream()
                .map(AchievementDto::from)
                .toList();
    }

    /**
     * 예측 결과에 따른 업적 확인 및 부여
     * @return 새로 획득한 업적 목록
     */
    @Transactional
    public List<Achievement> checkAndAwardAchievements(Long userId, UserScore userScore, int currentStreak) {
        List<Achievement> newAchievements = new ArrayList<>();

        // 첫 예측 업적
        if (userScore.getTotalPredictions() == 1) {
            awardIfNotEarned(userId, Achievement.FIRST_PREDICTION).ifPresent(newAchievements::add);
        }

        // 연승 업적
        if (currentStreak >= 3) {
            awardIfNotEarned(userId, Achievement.STREAK_3).ifPresent(newAchievements::add);
        }
        if (currentStreak >= 5) {
            awardIfNotEarned(userId, Achievement.STREAK_5).ifPresent(newAchievements::add);
        }
        if (currentStreak >= 7) {
            awardIfNotEarned(userId, Achievement.STREAK_7).ifPresent(newAchievements::add);
        }
        if (currentStreak >= 10) {
            awardIfNotEarned(userId, Achievement.STREAK_10).ifPresent(newAchievements::add);
        }

        // 레벨 업적
        int level = userScore.getUserLevel();
        if (level >= 10) {
            awardIfNotEarned(userId, Achievement.LEVEL_10).ifPresent(newAchievements::add);
        }
        if (level >= 30) {
            awardIfNotEarned(userId, Achievement.LEVEL_30).ifPresent(newAchievements::add);
        }
        if (level >= 60) {
            awardIfNotEarned(userId, Achievement.LEVEL_60).ifPresent(newAchievements::add);
        }

        // 명예의 전당 업적 (레벨 61+)
        if (level >= 61) {
            awardIfNotEarned(userId, Achievement.HALL_OF_FAME).ifPresent(newAchievements::add);
        }

        // 점수 업적
        long totalScore = userScore.getTotalScore() != null ? userScore.getTotalScore() : 0;
        if (totalScore >= 100000) {
            awardIfNotEarned(userId, Achievement.SCORE_100K).ifPresent(newAchievements::add);
        }
        if (totalScore >= 1000000) {
            awardIfNotEarned(userId, Achievement.SCORE_1M).ifPresent(newAchievements::add);
        }

        return newAchievements;
    }

    /**
     * 특정 업적 부여
     */
    @Transactional
    public void awardAchievement(Long userId, String achievementCode) {
        awardIfNotEarned(userId, achievementCode);
    }

    /**
     * 업적 획득 여부 확인
     */
    @Transactional(readOnly = true)
    public boolean hasAchievement(Long userId, String achievementCode) {
        return userAchievementRepository.hasAchievement(userId, achievementCode);
    }

    /**
     * 사용자 업적 개수 조회
     */
    @Transactional(readOnly = true)
    public Long countUserAchievements(Long userId) {
        return userAchievementRepository.countByUserId(userId);
    }

    /**
     * 최근 희귀 업적 획득 목록 (글로벌 피드)
     */
    @Transactional(readOnly = true)
    public List<AchievementDto> getRecentRareAchievements(int limit) {
        List<Achievement.Rarity> rareRarities = List.of(
                Achievement.Rarity.EPIC,
                Achievement.Rarity.LEGENDARY
        );
        return userAchievementRepository.findRecentRareAchievements(rareRarities, PageRequest.of(0, limit))
                .stream()
                .map(AchievementDto::from)
                .toList();
    }

    // ============================================
    // PRIVATE HELPER METHODS
    // ============================================

    private Optional<Achievement> awardIfNotEarned(Long userId, String achievementCode) {
        // 이미 획득했는지 확인
        if (userAchievementRepository.hasAchievement(userId, achievementCode)) {
            return Optional.empty();
        }

        // 업적 조회
        Optional<Achievement> achievementOpt = achievementRepository.findByCode(achievementCode);
        if (achievementOpt.isEmpty()) {
            log.warn("Achievement not found: {}", achievementCode);
            return Optional.empty();
        }

        Achievement achievement = achievementOpt.get();

        // 업적 부여
        UserAchievement userAchievement = UserAchievement.create(userId, achievement);
        userAchievementRepository.save(userAchievement);

        log.info("User {} earned achievement: {} ({})", userId, achievement.getNameKo(), achievementCode);
        return Optional.of(achievement);
    }
}
