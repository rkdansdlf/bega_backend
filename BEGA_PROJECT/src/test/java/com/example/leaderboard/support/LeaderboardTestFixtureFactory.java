package com.example.leaderboard.support;

import com.example.kbo.entity.GameEntity;
import com.example.leaderboard.entity.*;
import com.example.prediction.Prediction;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared test fixture factory for leaderboard unit tests.
 */
public final class LeaderboardTestFixtureFactory {

    private static final AtomicLong ID_COUNTER = new AtomicLong(1);

    private LeaderboardTestFixtureFactory() {
    }

    // ============================================
    // REFLECTION HELPER
    // ============================================

    public static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }

    private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    // ============================================
    // USER SCORE
    // ============================================

    public static UserScore freshUserScore(Long userId) {
        UserScore us = UserScore.createForUser(userId);
        setField(us, "id", ID_COUNTER.getAndIncrement());
        return us;
    }

    public static UserScore userScoreWithStats(Long userId, long totalScore, int streak,
                                                int maxStreak, int level, long exp,
                                                int correct, int total) {
        UserScore us = UserScore.builder()
                .userId(userId)
                .totalScore(totalScore)
                .seasonScore(totalScore)
                .monthlyScore(totalScore)
                .weeklyScore(totalScore)
                .currentStreak(streak)
                .maxStreak(maxStreak)
                .userLevel(level)
                .experiencePoints(exp)
                .correctPredictions(correct)
                .totalPredictions(total)
                .build();
        setField(us, "id", ID_COUNTER.getAndIncrement());
        return us;
    }

    // ============================================
    // ACHIEVEMENT
    // ============================================

    public static Achievement achievement(String code, Achievement.Rarity rarity) {
        Achievement a = Achievement.builder()
                .code(code)
                .nameKo(code + "_ko")
                .nameEn(code + "_en")
                .descriptionKo(code + " description")
                .rarity(rarity)
                .pointsRequired(0L)
                .build();
        setField(a, "id", ID_COUNTER.getAndIncrement());
        return a;
    }

    // ============================================
    // USER ACHIEVEMENT
    // ============================================

    public static UserAchievement userAchievement(Long userId, Achievement achievement) {
        UserAchievement ua = UserAchievement.create(userId, achievement);
        setField(ua, "id", ID_COUNTER.getAndIncrement());
        return ua;
    }

    // ============================================
    // POWERUP
    // ============================================

    public static UserPowerup powerup(Long userId, UserPowerup.PowerupType type, int quantity) {
        UserPowerup p = UserPowerup.create(userId, type, quantity);
        setField(p, "id", ID_COUNTER.getAndIncrement());
        return p;
    }

    // ============================================
    // ACTIVE POWERUP
    // ============================================

    public static ActivePowerup activePowerupForGame(Long userId, UserPowerup.PowerupType type, String gameId) {
        ActivePowerup ap = ActivePowerup.activateForGame(userId, type, gameId);
        setField(ap, "id", ID_COUNTER.getAndIncrement());
        return ap;
    }

    // ============================================
    // GAME ENTITY (uses canonical team codes)
    // ============================================

    public static GameEntity finishedGame(String gameId, String homeTeam, String awayTeam,
                                           int homeScore, int awayScore, LocalDate date) {
        return GameEntity.builder()
                .id(ID_COUNTER.getAndIncrement())
                .gameId(gameId)
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .homeScore(homeScore)
                .awayScore(awayScore)
                .gameDate(date)
                .isDummy(false)
                .build();
    }

    // ============================================
    // PREDICTION
    // ============================================

    public static Prediction prediction(Long userId, String gameId, String votedTeam) {
        Prediction p = Prediction.builder()
                .userId(userId)
                .gameId(gameId)
                .votedTeam(votedTeam)
                .build();
        setField(p, "id", ID_COUNTER.getAndIncrement());
        return p;
    }
}
