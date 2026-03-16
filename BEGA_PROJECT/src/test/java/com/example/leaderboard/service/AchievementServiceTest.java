package com.example.leaderboard.service;

import com.example.leaderboard.dto.AchievementDto;
import com.example.leaderboard.entity.Achievement;
import com.example.leaderboard.entity.UserAchievement;
import com.example.leaderboard.entity.UserScore;
import com.example.leaderboard.repository.AchievementRepository;
import com.example.leaderboard.repository.UserAchievementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.example.leaderboard.support.LeaderboardTestFixtureFactory.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AchievementServiceTest {

    @InjectMocks
    private AchievementService achievementService;

    @Mock
    private AchievementRepository achievementRepository;

    @Mock
    private UserAchievementRepository userAchievementRepository;

    // ============================================
    // checkAndAwardAchievements
    // ============================================

    @Test
    @DisplayName("첫 예측 시 FIRST_PREDICTION 업적 부여")
    void checkAndAwardAchievements_firstPrediction() {
        Long userId = 1L;
        UserScore us = userScoreWithStats(userId, 100, 1, 1, 1, 100, 1, 1);
        Achievement ach = achievement(Achievement.FIRST_PREDICTION, Achievement.Rarity.COMMON);

        when(userAchievementRepository.hasAchievement(userId, Achievement.FIRST_PREDICTION)).thenReturn(false);
        when(achievementRepository.findByCode(Achievement.FIRST_PREDICTION)).thenReturn(Optional.of(ach));
        when(userAchievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // Other achievement codes won't be checked because streak<3, level<10, score<100k
        List<Achievement> result = achievementService.checkAndAwardAchievements(userId, us, 1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo(Achievement.FIRST_PREDICTION);
        verify(userAchievementRepository).save(any(UserAchievement.class));
    }

    @Test
    @DisplayName("연승 3 달성 시 STREAK_3 부여")
    void checkAndAwardAchievements_streak3() {
        Long userId = 2L;
        UserScore us = userScoreWithStats(userId, 300, 3, 3, 1, 300, 3, 3);

        Achievement streak3 = achievement(Achievement.STREAK_3, Achievement.Rarity.COMMON);
        when(userAchievementRepository.hasAchievement(userId, Achievement.STREAK_3)).thenReturn(false);
        when(achievementRepository.findByCode(Achievement.STREAK_3)).thenReturn(Optional.of(streak3));
        when(userAchievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // totalPredictions != 1, so FIRST_PREDICTION skipped
        // streak < 5, level < 10, score < 100k → other checks skipped

        List<Achievement> result = achievementService.checkAndAwardAchievements(userId, us, 3);

        assertThat(result).extracting(Achievement::getCode).containsExactly(Achievement.STREAK_3);
    }

    @Test
    @DisplayName("연승 10 달성 시 STREAK_3/5/7/10 모두 부여 (미획득 상태)")
    void checkAndAwardAchievements_streak10AwardsMultiple() {
        Long userId = 3L;
        UserScore us = userScoreWithStats(userId, 5500, 10, 10, 1, 5500, 10, 10);

        for (String code : List.of(Achievement.STREAK_3, Achievement.STREAK_5,
                Achievement.STREAK_7, Achievement.STREAK_10)) {
            when(userAchievementRepository.hasAchievement(userId, code)).thenReturn(false);
            when(achievementRepository.findByCode(code)).thenReturn(
                    Optional.of(achievement(code, Achievement.Rarity.RARE)));
        }
        when(userAchievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Achievement> result = achievementService.checkAndAwardAchievements(userId, us, 10);

        assertThat(result).extracting(Achievement::getCode)
                .containsExactlyInAnyOrder(Achievement.STREAK_3, Achievement.STREAK_5,
                        Achievement.STREAK_7, Achievement.STREAK_10);
    }

    @Test
    @DisplayName("이미 획득한 업적은 건너뛴다")
    void checkAndAwardAchievements_alreadyEarnedSkipped() {
        Long userId = 4L;
        UserScore us = userScoreWithStats(userId, 100, 1, 1, 1, 100, 1, 1);

        when(userAchievementRepository.hasAchievement(userId, Achievement.FIRST_PREDICTION)).thenReturn(true);

        List<Achievement> result = achievementService.checkAndAwardAchievements(userId, us, 1);

        assertThat(result).isEmpty();
        verify(userAchievementRepository, never()).save(any());
    }

    @Test
    @DisplayName("레벨 30 달성 시 LEVEL_10 + LEVEL_30 부여")
    void checkAndAwardAchievements_levelAchievements() {
        Long userId = 5L;
        UserScore us = userScoreWithStats(userId, 50000, 0, 5, 30, 84100, 200, 400);

        for (String code : List.of(Achievement.LEVEL_10, Achievement.LEVEL_30)) {
            when(userAchievementRepository.hasAchievement(userId, code)).thenReturn(false);
            when(achievementRepository.findByCode(code)).thenReturn(
                    Optional.of(achievement(code, Achievement.Rarity.EPIC)));
        }
        when(userAchievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Achievement> result = achievementService.checkAndAwardAchievements(userId, us, 0);

        assertThat(result).extracting(Achievement::getCode)
                .containsExactlyInAnyOrder(Achievement.LEVEL_10, Achievement.LEVEL_30);
    }

    @Test
    @DisplayName("레벨 61 달성 시 HALL_OF_FAME 부여")
    void checkAndAwardAchievements_hallOfFame() {
        Long userId = 6L;
        UserScore us = userScoreWithStats(userId, 500000, 0, 10, 61, 360000, 1000, 2000);

        for (String code : List.of(Achievement.LEVEL_10, Achievement.LEVEL_30,
                Achievement.LEVEL_60, Achievement.HALL_OF_FAME, Achievement.SCORE_100K)) {
            when(userAchievementRepository.hasAchievement(userId, code)).thenReturn(false);
            when(achievementRepository.findByCode(code)).thenReturn(
                    Optional.of(achievement(code, Achievement.Rarity.LEGENDARY)));
        }
        when(userAchievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Achievement> result = achievementService.checkAndAwardAchievements(userId, us, 0);

        assertThat(result).extracting(Achievement::getCode).contains(Achievement.HALL_OF_FAME);
    }

    @Test
    @DisplayName("총점 100,000 달성 시 SCORE_100K 부여")
    void checkAndAwardAchievements_scoreThresholds() {
        Long userId = 7L;
        UserScore us = userScoreWithStats(userId, 100000, 0, 5, 10, 100000, 500, 1000);

        for (String code : List.of(Achievement.SCORE_100K, Achievement.LEVEL_10)) {
            when(userAchievementRepository.hasAchievement(userId, code)).thenReturn(false);
            when(achievementRepository.findByCode(code)).thenReturn(
                    Optional.of(achievement(code, Achievement.Rarity.EPIC)));
        }
        when(userAchievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Achievement> result = achievementService.checkAndAwardAchievements(userId, us, 0);

        assertThat(result).extracting(Achievement::getCode).contains(Achievement.SCORE_100K);
    }

    @Test
    @DisplayName("DB에 업적이 없으면 예외 없이 빈 리스트 반환")
    void checkAndAwardAchievements_achievementNotInDb() {
        Long userId = 8L;
        UserScore us = userScoreWithStats(userId, 100, 1, 1, 1, 100, 1, 1);

        when(userAchievementRepository.hasAchievement(userId, Achievement.FIRST_PREDICTION)).thenReturn(false);
        when(achievementRepository.findByCode(Achievement.FIRST_PREDICTION)).thenReturn(Optional.empty());

        List<Achievement> result = achievementService.checkAndAwardAchievements(userId, us, 1);

        assertThat(result).isEmpty();
    }

    // ============================================
    // checkSeatViewAchievements
    // ============================================

    @Test
    @DisplayName("첫 시야 사진 기여 시 FIRST_SEAT_VIEW 부여")
    void checkSeatViewAchievements_firstContribution() {
        Long userId = 10L;
        Achievement ach = achievement(Achievement.FIRST_SEAT_VIEW, Achievement.Rarity.COMMON);
        when(userAchievementRepository.hasAchievement(userId, Achievement.FIRST_SEAT_VIEW)).thenReturn(false);
        when(achievementRepository.findByCode(Achievement.FIRST_SEAT_VIEW)).thenReturn(Optional.of(ach));
        when(userAchievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Achievement> result = achievementService.checkSeatViewAchievements(userId, 1, 1);

        assertThat(result).extracting(Achievement::getCode).containsExactly(Achievement.FIRST_SEAT_VIEW);
    }

    @Test
    @DisplayName("5회 기여 시 FIRST_SEAT_VIEW + SEAT_VIEW_5 부여")
    void checkSeatViewAchievements_fiveContributions() {
        Long userId = 11L;
        for (String code : List.of(Achievement.FIRST_SEAT_VIEW, Achievement.SEAT_VIEW_5)) {
            when(userAchievementRepository.hasAchievement(userId, code)).thenReturn(false);
            when(achievementRepository.findByCode(code)).thenReturn(
                    Optional.of(achievement(code, Achievement.Rarity.RARE)));
        }
        when(userAchievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Achievement> result = achievementService.checkSeatViewAchievements(userId, 5, 1);

        assertThat(result).extracting(Achievement::getCode)
                .containsExactlyInAnyOrder(Achievement.FIRST_SEAT_VIEW, Achievement.SEAT_VIEW_5);
    }

    @Test
    @DisplayName("3개 이상 구장 방문 시 SEAT_VIEW_EXPLORER 부여")
    void checkSeatViewAchievements_explorer() {
        Long userId = 12L;
        for (String code : List.of(Achievement.FIRST_SEAT_VIEW, Achievement.SEAT_VIEW_EXPLORER)) {
            when(userAchievementRepository.hasAchievement(userId, code)).thenReturn(false);
            when(achievementRepository.findByCode(code)).thenReturn(
                    Optional.of(achievement(code, Achievement.Rarity.EPIC)));
        }
        when(userAchievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Achievement> result = achievementService.checkSeatViewAchievements(userId, 1, 3);

        assertThat(result).extracting(Achievement::getCode).contains(Achievement.SEAT_VIEW_EXPLORER);
    }

    @Test
    @DisplayName("기여 0회면 빈 리스트 반환")
    void checkSeatViewAchievements_noneQualified() {
        Long userId = 13L;

        List<Achievement> result = achievementService.checkSeatViewAchievements(userId, 0, 0);

        assertThat(result).isEmpty();
        verify(userAchievementRepository, never()).hasAchievement(anyLong(), anyString());
    }

    // ============================================
    // getUserAchievements
    // ============================================

    @Test
    @DisplayName("전체 업적 목록에서 획득 여부 플래그가 설정된다")
    void getUserAchievements_returnsAllWithEarnedFlag() {
        Long userId = 20L;
        Achievement a1 = achievement("A1", Achievement.Rarity.COMMON);
        Achievement a2 = achievement("A2", Achievement.Rarity.RARE);
        Achievement a3 = achievement("A3", Achievement.Rarity.EPIC);

        when(userAchievementRepository.findEarnedAchievementCodes(userId)).thenReturn(List.of("A2"));
        when(achievementRepository.findAllOrderByRarityDesc()).thenReturn(List.of(a1, a2, a3));
        UserAchievement ua = userAchievement(userId, a2);
        setField(ua, "earnedAt", LocalDateTime.now());
        when(userAchievementRepository.findByUserIdAndAchievementCode(userId, "A2")).thenReturn(Optional.of(ua));

        List<AchievementDto> result = achievementService.getUserAchievements(userId);

        assertThat(result).hasSize(3);
        assertThat(result.stream().filter(d -> Boolean.TRUE.equals(d.getEarned())).count()).isEqualTo(1);
    }

    // ============================================
    // getRecentAchievements
    // ============================================

    @Test
    @DisplayName("최근 업적 조회 시 PageRequest 위임")
    void getRecentAchievements_delegatesToRepo() {
        Long userId = 21L;
        UserAchievement ua = userAchievement(userId, achievement("A1", Achievement.Rarity.COMMON));
        setField(ua, "earnedAt", LocalDateTime.now());
        when(userAchievementRepository.findRecentByUserId(eq(userId), any(PageRequest.class)))
                .thenReturn(List.of(ua));

        List<AchievementDto> result = achievementService.getRecentAchievements(userId, 5);

        assertThat(result).hasSize(1);
        verify(userAchievementRepository).findRecentByUserId(userId, PageRequest.of(0, 5));
    }

    // ============================================
    // awardAchievement
    // ============================================

    @Test
    @DisplayName("미획득 업적을 부여한다")
    void awardAchievement_awardsWhenNotEarned() {
        Long userId = 30L;
        Achievement ach = achievement("TEST_ACH", Achievement.Rarity.RARE);
        when(userAchievementRepository.hasAchievement(userId, "TEST_ACH")).thenReturn(false);
        when(achievementRepository.findByCode("TEST_ACH")).thenReturn(Optional.of(ach));
        when(userAchievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        achievementService.awardAchievement(userId, "TEST_ACH");

        verify(userAchievementRepository).save(any(UserAchievement.class));
    }

    @Test
    @DisplayName("이미 획득한 업적은 저장하지 않는다")
    void awardAchievement_skipsWhenAlreadyEarned() {
        Long userId = 31L;
        when(userAchievementRepository.hasAchievement(userId, "TEST_ACH")).thenReturn(true);

        achievementService.awardAchievement(userId, "TEST_ACH");

        verify(userAchievementRepository, never()).save(any());
    }
}
