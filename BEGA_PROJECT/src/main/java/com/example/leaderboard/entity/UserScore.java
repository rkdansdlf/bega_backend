package com.example.leaderboard.entity;

import com.example.auth.entity.UserEntity;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 사용자 점수 및 레벨 추적 엔티티
 * 리더보드 시스템의 핵심 데이터를 관리합니다.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "user_scores")
@EntityListeners(AuditingEntityListener.class)
public class UserScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "total_score", nullable = false)
    @Builder.Default
    private Long totalScore = 0L;

    @Column(name = "season_score", nullable = false)
    @Builder.Default
    private Long seasonScore = 0L;

    @Column(name = "monthly_score", nullable = false)
    @Builder.Default
    private Long monthlyScore = 0L;

    @Column(name = "weekly_score", nullable = false)
    @Builder.Default
    private Long weeklyScore = 0L;

    @Column(name = "current_streak", nullable = false)
    @Builder.Default
    private Integer currentStreak = 0;

    @Column(name = "max_streak", nullable = false)
    @Builder.Default
    private Integer maxStreak = 0;

    @Column(name = "user_level", nullable = false)
    @Builder.Default
    private Integer userLevel = 1;

    @Column(name = "experience_points", nullable = false)
    @Builder.Default
    private Long experiencePoints = 0L;

    @Column(name = "correct_predictions", nullable = false)
    @Builder.Default
    private Integer correctPredictions = 0;

    @Column(name = "total_predictions", nullable = false)
    @Builder.Default
    private Integer totalPredictions = 0;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    // ============================================
    // RANK TIER ENUM
    // ============================================
    public enum RankTier {
        ROOKIE("루키", 1, 10),
        MINOR_LEAGUER("마이너리거", 11, 30),
        MAJOR_LEAGUER("메이저리거", 31, 60),
        HALL_OF_FAME("명예의 전당", 61, 99);

        private final String koreanName;
        private final int minLevel;
        private final int maxLevel;

        RankTier(String koreanName, int minLevel, int maxLevel) {
            this.koreanName = koreanName;
            this.minLevel = minLevel;
            this.maxLevel = maxLevel;
        }

        public String getKoreanName() {
            return koreanName;
        }

        public static RankTier fromLevel(int level) {
            for (RankTier tier : values()) {
                if (level >= tier.minLevel && level <= tier.maxLevel) {
                    return tier;
                }
            }
            return HALL_OF_FAME;
        }
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    /**
     * 현재 레벨에 해당하는 랭크 티어 반환
     */
    public RankTier getRankTier() {
        return RankTier.fromLevel(this.userLevel);
    }

    /**
     * 랭크 티어 이름 반환 (API용)
     */
    public String getRankTitle() {
        return getRankTier().name();
    }

    /**
     * 적중률 계산 (%)
     */
    public Double getAccuracy() {
        if (totalPredictions == null || totalPredictions == 0) {
            return 0.0;
        }
        return (correctPredictions.doubleValue() / totalPredictions.doubleValue()) * 100;
    }

    /**
     * 점수 추가 및 레벨 업데이트
     * @param points 획득 점수
     * @param streakCount 현재 연승 수
     */
    public void addScore(int points, int streakCount) {
        // 콤보 배율 적용 (연승 시 n배)
        int finalPoints = points * Math.max(1, streakCount);

        this.totalScore = (this.totalScore == null ? 0 : this.totalScore) + finalPoints;
        this.seasonScore = (this.seasonScore == null ? 0 : this.seasonScore) + finalPoints;
        this.monthlyScore = (this.monthlyScore == null ? 0 : this.monthlyScore) + finalPoints;
        this.weeklyScore = (this.weeklyScore == null ? 0 : this.weeklyScore) + finalPoints;
        this.experiencePoints = (this.experiencePoints == null ? 0 : this.experiencePoints) + finalPoints;

        // 연승 업데이트
        this.currentStreak = streakCount;
        if (streakCount > (this.maxStreak == null ? 0 : this.maxStreak)) {
            this.maxStreak = streakCount;
        }

        // 레벨 업데이트
        updateLevel();
    }

    /**
     * 맞힌 예측 기록
     */
    public void recordCorrectPrediction() {
        this.correctPredictions = (this.correctPredictions == null ? 0 : this.correctPredictions) + 1;
        this.totalPredictions = (this.totalPredictions == null ? 0 : this.totalPredictions) + 1;
    }

    /**
     * 틀린 예측 기록 (연승 초기화)
     */
    public void recordIncorrectPrediction() {
        this.totalPredictions = (this.totalPredictions == null ? 0 : this.totalPredictions) + 1;
        this.currentStreak = 0;
    }

    /**
     * 경험치 기반 레벨 계산
     * Level = min(99, floor(sqrt(exp / 100)) + 1)
     */
    private void updateLevel() {
        long exp = this.experiencePoints == null ? 0 : this.experiencePoints;
        int calculatedLevel = (int) Math.floor(Math.sqrt(exp / 100.0)) + 1;
        this.userLevel = Math.min(99, calculatedLevel);
    }

    /**
     * 다음 레벨까지 필요한 경험치 계산
     */
    public long getNextLevelExp() {
        int nextLevel = Math.min(99, this.userLevel + 1);
        return (long) Math.pow(nextLevel - 1, 2) * 100;
    }

    /**
     * 주간 점수 리셋 (스케줄러용)
     */
    public void resetWeeklyScore() {
        this.weeklyScore = 0L;
    }

    /**
     * 월간 점수 리셋 (스케줄러용)
     */
    public void resetMonthlyScore() {
        this.monthlyScore = 0L;
    }

    /**
     * 시즌 점수 리셋 (스케줄러용)
     */
    public void resetSeasonScore() {
        this.seasonScore = 0L;
    }

    /**
     * 정적 팩토리 메서드: 새 사용자용 UserScore 생성
     */
    public static UserScore createForUser(Long userId) {
        return UserScore.builder()
                .userId(userId)
                .totalScore(0L)
                .seasonScore(0L)
                .monthlyScore(0L)
                .weeklyScore(0L)
                .currentStreak(0)
                .maxStreak(0)
                .userLevel(1)
                .experiencePoints(0L)
                .correctPredictions(0)
                .totalPredictions(0)
                .build();
    }
}
