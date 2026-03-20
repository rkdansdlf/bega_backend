package com.example.leaderboard.service;

import com.example.leaderboard.dto.ScoreResultDto;
import com.example.leaderboard.dto.SeatViewRewardDto;
import com.example.leaderboard.entity.*;
import com.example.leaderboard.entity.UserPowerup.PowerupType;
import com.example.leaderboard.repository.ActivePowerupRepository;
import com.example.leaderboard.repository.ScoreEventRepository;
import com.example.leaderboard.repository.UserScoreRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.example.leaderboard.support.LeaderboardTestFixtureFactory.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScoringServiceTest {

    @InjectMocks
    private ScoringService scoringService;

    @Mock
    private UserScoreRepository userScoreRepository;

    @Mock
    private ScoreEventRepository scoreEventRepository;

    @Mock
    private ActivePowerupRepository activePowerupRepository;

    @Mock
    private AchievementService achievementService;

    // ============================================
    // processPredictionResult — correct prediction
    // ============================================

    @Test
    @DisplayName("첫 맞힌 예측: streak 0→1, 기본 100점")
    void processPredictionResult_correctFirstPrediction() {
        Long userId = 1L;
        UserScore us = freshUserScore(userId);
        when(userScoreRepository.findByUserId(userId)).thenReturn(Optional.of(us));
        when(scoreEventRepository.existsByPredictionIdAndUserId(100L, userId)).thenReturn(false);
        when(activePowerupRepository.findActiveForGame(userId, "game1")).thenReturn(Collections.emptyList());
        when(scoreEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userScoreRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(achievementService.checkAndAwardAchievements(eq(userId), any(), eq(1))).thenReturn(Collections.emptyList());

        ScoreResultDto result = scoringService.processPredictionResult(userId, 100L, "game1", true, false);

        assertThat(result.getCorrect()).isTrue();
        assertThat(result.getBaseScore()).isEqualTo(100);
        // streak 1 → multiplier 1.0, totalEarned = 100
        assertThat(result.getTotalEarned()).isEqualTo(100);
        assertThat(result.getCurrentStreak()).isEqualTo(1);
        assertThat(result.getBonusScore()).isZero();
    }

    @Test
    @DisplayName("연승 3: streak 2→3, 점수 300 (100×3)")
    void processPredictionResult_correctWithStreak3() {
        Long userId = 2L;
        // currentStreak=2, recordCorrectPrediction increments correct/total but not streak
        // newStreak = currentStreak + 1 = 3
        UserScore us = userScoreWithStats(userId, 300, 2, 2, 1, 300, 2, 2);
        when(userScoreRepository.findByUserId(userId)).thenReturn(Optional.of(us));
        when(scoreEventRepository.existsByPredictionIdAndUserId(101L, userId)).thenReturn(false);
        when(activePowerupRepository.findActiveForGame(userId, "game2")).thenReturn(Collections.emptyList());
        when(scoreEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userScoreRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(achievementService.checkAndAwardAchievements(eq(userId), any(), eq(3))).thenReturn(Collections.emptyList());

        ScoreResultDto result = scoringService.processPredictionResult(userId, 101L, "game2", true, false);

        assertThat(result.getTotalEarned()).isEqualTo(300); // 100 * 3
        assertThat(result.getCurrentStreak()).isEqualTo(3);
        assertThat(result.getMultiplier()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("이변 보너스: upset=true → +50 보너스")
    void processPredictionResult_correctWithUpsetBonus() {
        Long userId = 3L;
        UserScore us = freshUserScore(userId);
        when(userScoreRepository.findByUserId(userId)).thenReturn(Optional.of(us));
        when(scoreEventRepository.existsByPredictionIdAndUserId(102L, userId)).thenReturn(false);
        when(activePowerupRepository.findActiveForGame(userId, "game3")).thenReturn(Collections.emptyList());
        when(scoreEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userScoreRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(achievementService.checkAndAwardAchievements(eq(userId), any(), eq(1))).thenReturn(Collections.emptyList());

        ScoreResultDto result = scoringService.processPredictionResult(userId, 102L, "game3", true, true);

        assertThat(result.getTotalEarned()).isEqualTo(150); // 100 + 50
        assertThat(result.getBonusScore()).isEqualTo(50);
        // upset event saved separately
        verify(scoreEventRepository, times(2)).save(any(ScoreEvent.class));
    }

    @Test
    @DisplayName("매직배트 활성: streak 0→1, ×2 = 200점")
    void processPredictionResult_correctWithMagicBat() {
        Long userId = 4L;
        UserScore us = freshUserScore(userId);
        ActivePowerup magicBat = activePowerupForGame(userId, PowerupType.MAGIC_BAT, "game4");

        when(userScoreRepository.findByUserId(userId)).thenReturn(Optional.of(us));
        when(scoreEventRepository.existsByPredictionIdAndUserId(103L, userId)).thenReturn(false);
        when(activePowerupRepository.findActiveForGame(userId, "game4")).thenReturn(List.of(magicBat));
        when(activePowerupRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(scoreEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userScoreRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(achievementService.checkAndAwardAchievements(eq(userId), any(), eq(1))).thenReturn(Collections.emptyList());

        ScoreResultDto result = scoringService.processPredictionResult(userId, 103L, "game4", true, false);

        assertThat(result.getTotalEarned()).isEqualTo(200); // 100 * 1 * 2
        assertThat(result.getMultiplier()).isEqualTo(2.0);
        assertThat(result.getAppliedPowerups()).contains("매직 배트");
        assertThat(magicBat.getUsed()).isTrue();
    }

    @Test
    @DisplayName("연승 3 + 매직배트: 100×3×2 = 600점")
    void processPredictionResult_correctWithStreakAndMagicBat() {
        Long userId = 5L;
        UserScore us = userScoreWithStats(userId, 300, 2, 2, 1, 300, 2, 2);
        ActivePowerup magicBat = activePowerupForGame(userId, PowerupType.MAGIC_BAT, "game5");

        when(userScoreRepository.findByUserId(userId)).thenReturn(Optional.of(us));
        when(scoreEventRepository.existsByPredictionIdAndUserId(104L, userId)).thenReturn(false);
        when(activePowerupRepository.findActiveForGame(userId, "game5")).thenReturn(List.of(magicBat));
        when(activePowerupRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(scoreEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userScoreRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(achievementService.checkAndAwardAchievements(eq(userId), any(), eq(3))).thenReturn(Collections.emptyList());

        ScoreResultDto result = scoringService.processPredictionResult(userId, 104L, "game5", true, false);

        assertThat(result.getTotalEarned()).isEqualTo(600); // 100 * 3 * 2
        assertThat(result.getMultiplier()).isEqualTo(6.0); // 3 * 2
    }

    @Test
    @DisplayName("경험치 임계값을 넘기면 leveledUp=true")
    void processPredictionResult_correctTriggersLevelUp() {
        Long userId = 6L;
        // Level formula: min(99, floor(sqrt(exp/100)) + 1)
        // At exp=0, level=1. After gaining 100 → exp=100, level = floor(1)+1 = 2
        UserScore us = freshUserScore(userId);
        when(userScoreRepository.findByUserId(userId)).thenReturn(Optional.of(us));
        when(scoreEventRepository.existsByPredictionIdAndUserId(105L, userId)).thenReturn(false);
        when(activePowerupRepository.findActiveForGame(userId, "game6")).thenReturn(Collections.emptyList());
        when(scoreEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userScoreRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(achievementService.checkAndAwardAchievements(eq(userId), any(), eq(1))).thenReturn(Collections.emptyList());

        ScoreResultDto result = scoringService.processPredictionResult(userId, 105L, "game6", true, false);

        assertThat(result.getLeveledUp()).isTrue();
        assertThat(result.getPreviousLevel()).isEqualTo(1);
        assertThat(result.getNewLevel()).isEqualTo(2); // sqrt(100/100)+1 = 2
    }

    @Test
    @DisplayName("새로운 최대 연승 갱신 시 maxStreak이 업데이트된다")
    void processPredictionResult_correctUpdatesMaxStreak() {
        Long userId = 7L;
        // maxStreak=2, currentStreak=2 → newStreak=3
        // addScore() internally updates maxStreak to 3 before the DTO check
        UserScore us = userScoreWithStats(userId, 300, 2, 2, 1, 300, 2, 2);
        when(userScoreRepository.findByUserId(userId)).thenReturn(Optional.of(us));
        when(scoreEventRepository.existsByPredictionIdAndUserId(106L, userId)).thenReturn(false);
        when(activePowerupRepository.findActiveForGame(userId, "game7")).thenReturn(Collections.emptyList());
        when(scoreEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userScoreRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(achievementService.checkAndAwardAchievements(eq(userId), any(), eq(3))).thenReturn(Collections.emptyList());

        ScoreResultDto result = scoringService.processPredictionResult(userId, 106L, "game7", true, false);

        // maxStreak correctly updated on entity
        assertThat(us.getMaxStreak()).isEqualTo(3);
        assertThat(result.getCurrentStreak()).isEqualTo(3);
    }

    // ============================================
    // processPredictionResult — incorrect
    // ============================================

    @Test
    @DisplayName("틀린 예측: 연승 리셋, ScoreResultDto.incorrect 반환")
    void processPredictionResult_incorrectResetsStreak() {
        Long userId = 10L;
        UserScore us = freshUserScore(userId);
        when(userScoreRepository.findByUserId(userId)).thenReturn(Optional.of(us));
        when(scoreEventRepository.existsByPredictionIdAndUserId(200L, userId)).thenReturn(false);
        when(userScoreRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ScoreResultDto result = scoringService.processPredictionResult(userId, 200L, "game10", false, false);

        assertThat(result.getCorrect()).isFalse();
        assertThat(result.getTotalEarned()).isZero();
        assertThat(result.getCurrentStreak()).isZero();
        assertThat(us.getCurrentStreak()).isZero();
    }

    @Test
    @DisplayName("연승 5 상태에서 틀리면 streak=0으로 리셋")
    void processPredictionResult_incorrectWithExistingStreak() {
        Long userId = 11L;
        UserScore us = userScoreWithStats(userId, 1500, 5, 5, 3, 1500, 5, 5);
        when(userScoreRepository.findByUserId(userId)).thenReturn(Optional.of(us));
        when(scoreEventRepository.existsByPredictionIdAndUserId(201L, userId)).thenReturn(false);
        when(userScoreRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ScoreResultDto result = scoringService.processPredictionResult(userId, 201L, "game11", false, false);

        assertThat(result.getCorrect()).isFalse();
        assertThat(us.getCurrentStreak()).isZero();
        assertThat(us.getTotalPredictions()).isEqualTo(6);
    }

    // ============================================
    // processPredictionResult — idempotency & edge cases
    // ============================================

    @Test
    @DisplayName("중복 처리 시 캐시된 결과 반환, 새 저장 없음")
    void processPredictionResult_idempotentDuplicate() {
        Long userId = 15L;
        UserScore us = userScoreWithStats(userId, 500, 3, 3, 2, 500, 5, 5);
        when(userScoreRepository.findByUserId(userId)).thenReturn(Optional.of(us));
        when(scoreEventRepository.existsByPredictionIdAndUserId(300L, userId)).thenReturn(true);

        ScoreResultDto result = scoringService.processPredictionResult(userId, 300L, "game15", true, false);

        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getNewTotalScore()).isEqualTo(500L);
        verify(scoreEventRepository, never()).save(any());
        verify(userScoreRepository, never()).save(any());
    }

    @Test
    @DisplayName("신규 사용자는 UserScore 자동 생성 후 처리")
    void processPredictionResult_newUserCreated() {
        Long userId = 16L;
        UserScore freshUs = freshUserScore(userId);
        when(userScoreRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(userScoreRepository.save(any(UserScore.class))).thenReturn(freshUs);
        when(scoreEventRepository.existsByPredictionIdAndUserId(301L, userId)).thenReturn(false);
        when(activePowerupRepository.findActiveForGame(userId, "game16")).thenReturn(Collections.emptyList());
        when(scoreEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(achievementService.checkAndAwardAchievements(eq(userId), any(), eq(1))).thenReturn(Collections.emptyList());

        ScoreResultDto result = scoringService.processPredictionResult(userId, 301L, "game16", true, false);

        assertThat(result.getCorrect()).isTrue();
        // save called: 1 for create + 1 for update after scoring
        verify(userScoreRepository, atLeast(2)).save(any(UserScore.class));
    }

    // ============================================
    // processPerfectDay
    // ============================================

    @Test
    @DisplayName("기존 사용자에게 퍼펙트데이 보너스 200점 추가")
    void processPerfectDay_addsBonus() {
        Long userId = 20L;
        UserScore us = userScoreWithStats(userId, 1000, 3, 3, 4, 1000, 10, 10);
        when(userScoreRepository.findByUserId(userId)).thenReturn(Optional.of(us));
        when(userScoreRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(scoreEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        scoringService.processPerfectDay(userId, 5);

        assertThat(us.getTotalScore()).isEqualTo(1200L);
        assertThat(us.getSeasonScore()).isEqualTo(1200L);
        assertThat(us.getExperiencePoints()).isEqualTo(1200L);
        verify(achievementService).awardAchievement(userId, Achievement.PERFECT_DAY);
    }

    @Test
    @DisplayName("신규 사용자도 퍼펙트데이 처리 가능")
    void processPerfectDay_newUser() {
        Long userId = 21L;
        UserScore freshUs = freshUserScore(userId);
        when(userScoreRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(userScoreRepository.save(any(UserScore.class))).thenReturn(freshUs);
        when(scoreEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        scoringService.processPerfectDay(userId, 3);

        assertThat(freshUs.getTotalScore()).isEqualTo(200L);
        verify(achievementService).awardAchievement(userId, Achievement.PERFECT_DAY);
    }

    // ============================================
    // processSeatViewReward
    // ============================================

    @Test
    @DisplayName("첫 시야 기여: 100점, firstContribution=true")
    void processSeatViewReward_firstContribution() {
        Long userId = 25L;
        UserScore us = freshUserScore(userId);
        when(scoreEventRepository.existsByDiaryIdAndUserId(500L, userId)).thenReturn(false);
        when(userScoreRepository.findByUserId(userId)).thenReturn(Optional.of(us));
        when(scoreEventRepository.countSeatViewContributionsByUserId(userId)).thenReturn(0L);
        when(scoreEventRepository.countDistinctStadiumsBySeatViewUserId(userId)).thenReturn(1L);
        when(userScoreRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(scoreEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(achievementService.checkSeatViewAchievements(eq(userId), eq(1L), eq(1L)))
                .thenReturn(Collections.emptyList());

        SeatViewRewardDto result = scoringService.processSeatViewReward(userId, 500L, "잠실야구장");

        assertThat(result.getPointsEarned()).isEqualTo(100);
        assertThat(result.isFirstContribution()).isTrue();
        assertThat(result.getTotalContributions()).isEqualTo(1);
    }

    @Test
    @DisplayName("이후 기여: 50점, firstContribution=false")
    void processSeatViewReward_subsequentContribution() {
        Long userId = 26L;
        UserScore us = userScoreWithStats(userId, 100, 0, 0, 1, 100, 0, 0);
        when(scoreEventRepository.existsByDiaryIdAndUserId(501L, userId)).thenReturn(false);
        when(userScoreRepository.findByUserId(userId)).thenReturn(Optional.of(us));
        when(scoreEventRepository.countSeatViewContributionsByUserId(userId)).thenReturn(3L);
        when(scoreEventRepository.countDistinctStadiumsBySeatViewUserId(userId)).thenReturn(2L);
        when(userScoreRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(scoreEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(achievementService.checkSeatViewAchievements(eq(userId), eq(4L), eq(2L)))
                .thenReturn(Collections.emptyList());

        SeatViewRewardDto result = scoringService.processSeatViewReward(userId, 501L, "수원야구장");

        assertThat(result.getPointsEarned()).isEqualTo(50);
        assertThat(result.isFirstContribution()).isFalse();
        assertThat(result.getTotalContributions()).isEqualTo(4);
    }

    @Test
    @DisplayName("중복 시야 기여 시 null 반환")
    void processSeatViewReward_idempotentDuplicate() {
        Long userId = 27L;
        when(scoreEventRepository.existsByDiaryIdAndUserId(502L, userId)).thenReturn(true);

        SeatViewRewardDto result = scoringService.processSeatViewReward(userId, 502L, "잠실야구장");

        assertThat(result).isNull();
        verify(userScoreRepository, never()).save(any());
    }

    // ============================================
    // useGoldenGlove
    // ============================================

    @Test
    @DisplayName("활성 골든글러브 사용 성공")
    void useGoldenGlove_successfulUse() {
        Long userId = 30L;
        ActivePowerup gg = activePowerupForGame(userId, PowerupType.GOLDEN_GLOVE, "game30");
        when(activePowerupRepository.findActiveByUserIdAndType(eq(userId), eq(PowerupType.GOLDEN_GLOVE), any(LocalDateTime.class)))
                .thenReturn(List.of(gg));
        when(activePowerupRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        boolean result = scoringService.useGoldenGlove(userId, "game30");

        assertThat(result).isTrue();
        assertThat(gg.getUsed()).isTrue();
    }

    @Test
    @DisplayName("활성 골든글러브가 없으면 false")
    void useGoldenGlove_noneAvailable() {
        Long userId = 31L;
        when(activePowerupRepository.findActiveByUserIdAndType(eq(userId), eq(PowerupType.GOLDEN_GLOVE), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        boolean result = scoringService.useGoldenGlove(userId, "game31");

        assertThat(result).isFalse();
        verify(activePowerupRepository, never()).save(any());
    }
}
