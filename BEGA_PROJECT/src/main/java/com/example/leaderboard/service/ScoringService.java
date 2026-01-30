package com.example.leaderboard.service;

import com.example.leaderboard.dto.ScoreResultDto;
import com.example.leaderboard.entity.*;
import com.example.leaderboard.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 점수 계산 서비스
 * 예측 결과에 따른 점수 계산 및 파워업 적용을 담당합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScoringService {

    private final UserScoreRepository userScoreRepository;
    private final ScoreEventRepository scoreEventRepository;
    private final ActivePowerupRepository activePowerupRepository;
    private final AchievementService achievementService;

    private static final int BASE_CORRECT_SCORE = 100;
    private static final int UPSET_BONUS = 50;
    private static final int PERFECT_DAY_BONUS = 200;

    /**
     * 예측 결과 처리
     * 
     * @param userId       사용자 ID
     * @param predictionId 예측 ID
     * @param gameId       게임 ID
     * @param isCorrect    예측 적중 여부
     * @param isUpset      이변 여부 (약팀이 강팀을 이긴 경우)
     * @return 점수 결과 DTO
     */
    @Transactional
    public ScoreResultDto processPredictionResult(Long userId, Long predictionId, String gameId,
            boolean isCorrect, boolean isUpset) {
        // 사용자 점수 조회 또는 생성
        UserScore userScore = userScoreRepository.findByUserId(userId)
                .orElseGet(() -> userScoreRepository.save(UserScore.createForUser(userId)));

        // 중복 처리 방지 (Idempotency)
        if (scoreEventRepository.existsByPredictionIdAndUserId(predictionId, userId)) {
            log.info("Prediction {} for user {} already processed. Skipping.", predictionId, userId);
            return ScoreResultDto.builder()
                    .userId(userId)
                    .correct(isCorrect)
                    .newTotalScore(userScore.getTotalScore())
                    .currentStreak(userScore.getCurrentStreak())
                    .build();
        }

        int previousLevel = userScore.getUserLevel();

        if (!isCorrect) {
            // 틀린 예측
            userScore.recordIncorrectPrediction();
            userScoreRepository.save(userScore);

            log.info("User {} prediction incorrect. Streak reset.", userId);
            return ScoreResultDto.incorrect(userId, 0);
        }

        // 맞힌 예측 처리
        userScore.recordCorrectPrediction();
        int newStreak = userScore.getCurrentStreak() + 1;

        // 기본 점수 계산
        int baseScore = BASE_CORRECT_SCORE;
        double streakMultiplier = (double) Math.max(1, newStreak);
        double powerupMultiplier = 1.0;
        int bonusScore = 0;
        List<String> appliedPowerups = new ArrayList<>();

        // 파워업 확인 및 적용
        List<ActivePowerup> activePowerups = activePowerupRepository.findActiveForGame(userId, gameId);
        for (ActivePowerup powerup : activePowerups) {
            if (powerup.getPowerupType() == UserPowerup.PowerupType.MAGIC_BAT) {
                powerupMultiplier *= powerup.getPowerupType().getMultiplier();
                appliedPowerups.add(powerup.getPowerupType().getKoreanName());
                powerup.markAsUsed();
                activePowerupRepository.save(powerup);
                log.info("User {} used MAGIC_BAT powerup for game {}", userId, gameId);
            }
        }

        // 최종 점수 계산
        double totalMultiplier = streakMultiplier * powerupMultiplier;
        int streakScore = (int) Math.round(baseScore * streakMultiplier);
        int totalEarned = (int) Math.round(baseScore * totalMultiplier);

        // 이변 보너스
        if (isUpset) {
            bonusScore += UPSET_BONUS;
            totalEarned += UPSET_BONUS;

            // 이변 보너스 이벤트 기록
            ScoreEvent upsetEvent = ScoreEvent.createUpsetBonus(userId, predictionId, gameId);
            scoreEventRepository.save(upsetEvent);
        }

        // 점수 추가 (연승 배율까지 적용)
        userScore.addScore(baseScore, newStreak);

        // 보너스 점수 별도 추가
        if (bonusScore > 0) {
            userScore.setTotalScore(userScore.getTotalScore() + bonusScore);
            userScore.setSeasonScore(userScore.getSeasonScore() + bonusScore);
            userScore.setMonthlyScore(userScore.getMonthlyScore() + bonusScore);
            userScore.setWeeklyScore(userScore.getWeeklyScore() + bonusScore);
            userScore.setExperiencePoints(userScore.getExperiencePoints() + bonusScore);
        }

        // 파워업 보너스 점수 추가
        if (powerupMultiplier > 1.0) {
            int powerupBonus = (int) Math.round(streakScore * (powerupMultiplier - 1.0));
            userScore.setTotalScore(userScore.getTotalScore() + powerupBonus);
            userScore.setSeasonScore(userScore.getSeasonScore() + powerupBonus);
            userScore.setMonthlyScore(userScore.getMonthlyScore() + powerupBonus);
            userScore.setWeeklyScore(userScore.getWeeklyScore() + powerupBonus);
            userScore.setExperiencePoints(userScore.getExperiencePoints() + powerupBonus);
        }

        // 최대 연승 업데이트
        boolean isNewMaxStreak = newStreak > userScore.getMaxStreak();
        if (isNewMaxStreak) {
            userScore.setMaxStreak(newStreak);
        }

        userScoreRepository.save(userScore);

        // 점수 이벤트 기록
        ScoreEvent scoreEvent = ScoreEvent.createCorrectPrediction(userId, predictionId, gameId, newStreak);
        scoreEventRepository.save(scoreEvent);

        // 파워업 배율 이벤트 기록 (배율이 연승 외에 추가 적용된 경우)
        if (!appliedPowerups.isEmpty()) {
            ScoreEvent powerupEvent = ScoreEvent.createPowerUpBonus(
                    userId, predictionId, gameId,
                    streakScore, // 연승 배율 적용된 점수
                    powerupMultiplier, // 파워업 배율
                    String.join(", ", appliedPowerups));
            scoreEventRepository.save(powerupEvent);
        }

        // 업적 확인
        List<Achievement> unlockedAchievements = achievementService.checkAndAwardAchievements(userId, userScore,
                newStreak);

        // 레벨업 확인
        boolean leveledUp = userScore.getUserLevel() > previousLevel;

        log.info("User {} prediction correct! Streak: {}, Score earned: {}, Level: {}",
                userId, newStreak, totalEarned, userScore.getUserLevel());

        return ScoreResultDto.builder()
                .userId(userId)
                .correct(true)
                .baseScore(baseScore)
                .multiplier(totalMultiplier)
                .bonusScore(bonusScore)
                .totalEarned(totalEarned)
                .newTotalScore(userScore.getTotalScore())
                .newLevel(userScore.getUserLevel())
                .newRankTier(userScore.getRankTier().name())
                .experiencePoints(userScore.getExperiencePoints())
                .currentStreak(newStreak)
                .isNewMaxStreak(isNewMaxStreak)
                .appliedPowerups(appliedPowerups)
                .unlockedAchievements(unlockedAchievements.stream()
                        .map(a -> com.example.leaderboard.dto.AchievementDto.from(a, true, LocalDateTime.now()))
                        .toList())
                .leveledUp(leveledUp)
                .previousLevel(previousLevel)
                .build();
    }

    /**
     * 퍼펙트 데이 보너스 처리
     * 하루의 모든 경기를 맞힌 경우 호출
     */
    @Transactional
    public void processPerfectDay(Long userId, int gamesWon) {
        UserScore userScore = userScoreRepository.findByUserId(userId)
                .orElseGet(() -> userScoreRepository.save(UserScore.createForUser(userId)));

        // 퍼펙트 데이 보너스 추가
        userScore.setTotalScore(userScore.getTotalScore() + PERFECT_DAY_BONUS);
        userScore.setSeasonScore(userScore.getSeasonScore() + PERFECT_DAY_BONUS);
        userScore.setMonthlyScore(userScore.getMonthlyScore() + PERFECT_DAY_BONUS);
        userScore.setWeeklyScore(userScore.getWeeklyScore() + PERFECT_DAY_BONUS);
        userScore.setExperiencePoints(userScore.getExperiencePoints() + PERFECT_DAY_BONUS);

        userScoreRepository.save(userScore);

        // 퍼펙트 데이 이벤트 기록
        ScoreEvent event = ScoreEvent.createPerfectDay(userId, gamesWon);
        scoreEventRepository.save(event);

        // 퍼펙트 데이 업적 확인
        achievementService.awardAchievement(userId, Achievement.PERFECT_DAY);

        log.info("User {} achieved PERFECT DAY! {} games won.", userId, gamesWon);
    }

    /**
     * 골든 글러브 파워업으로 연승 보호
     * 틀린 예측이지만 연승을 유지
     */
    @Transactional
    public boolean useGoldenGlove(Long userId, String gameId) {
        List<ActivePowerup> activePowerups = activePowerupRepository
                .findActiveByUserIdAndType(userId, UserPowerup.PowerupType.GOLDEN_GLOVE, LocalDateTime.now());

        if (activePowerups.isEmpty()) {
            return false;
        }

        // 골든 글러브 사용
        ActivePowerup goldenGlove = activePowerups.get(0);
        goldenGlove.markAsUsed();
        activePowerupRepository.save(goldenGlove);

        log.info("User {} used GOLDEN_GLOVE to protect streak for game {}", userId, gameId);
        return true;
    }
}
