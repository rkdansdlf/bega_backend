package com.example.leaderboard.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 점수 이벤트 기록 엔티티
 * 모든 점수 획득 이력을 추적합니다.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "score_events")
@EntityListeners(AuditingEntityListener.class)
public class ScoreEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "prediction_id")
    private Long predictionId;

    @Column(name = "game_id", length = 50)
    private String gameId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    @Column(name = "base_score", nullable = false)
    private Integer baseScore;

    @Column(name = "multiplier", nullable = false)
    @Builder.Default
    private Double multiplier = 1.0;

    @Column(name = "final_score", nullable = false)
    private Integer finalScore;

    @Column(name = "streak_count")
    @Builder.Default
    private Integer streakCount = 0;

    @Column(name = "description", length = 500)
    private String description;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // ============================================
    // EVENT TYPE ENUM
    // ============================================
    public enum EventType {
        /** 정확한 예측 (기본 100점) */
        CORRECT_PREDICTION("정확한 예측", 100),

        /** 연승 보너스 (연승 수 × 기본 점수) */
        STREAK_BONUS("연승 보너스", 0),

        /** 이변 예측 성공 (약팀이 강팀 이길 때 예측 성공) */
        UPSET_BONUS("이변 예측", 50),

        /** 퍼펙트 데이 (하루 모든 경기 맞힘) */
        PERFECT_DAY("퍼펙트 데이", 200),

        /** 파워업 배율 적용 (매직 배트 등) */
        POWER_UP_MULTIPLIER("파워업 보너스", 0);

        private final String koreanName;
        private final int defaultPoints;

        EventType(String koreanName, int defaultPoints) {
            this.koreanName = koreanName;
            this.defaultPoints = defaultPoints;
        }

        public String getKoreanName() {
            return koreanName;
        }

        public int getDefaultPoints() {
            return defaultPoints;
        }
    }

    // ============================================
    // STATIC FACTORY METHODS
    // ============================================

    /**
     * 정확한 예측 이벤트 생성
     */
    public static ScoreEvent createCorrectPrediction(Long userId, Long predictionId, String gameId, int streak) {
        int baseScore = EventType.CORRECT_PREDICTION.getDefaultPoints();
        int finalScore = baseScore * Math.max(1, streak);

        return ScoreEvent.builder()
                .userId(userId)
                .predictionId(predictionId)
                .gameId(gameId)
                .eventType(EventType.CORRECT_PREDICTION)
                .baseScore(baseScore)
                .multiplier((double) Math.max(1, streak))
                .finalScore(finalScore)
                .streakCount(streak)
                .description(String.format("%d연승! +%d점", streak, finalScore))
                .build();
    }

    /**
     * 이변 예측 보너스 이벤트 생성
     */
    public static ScoreEvent createUpsetBonus(Long userId, Long predictionId, String gameId) {
        int baseScore = EventType.UPSET_BONUS.getDefaultPoints();

        return ScoreEvent.builder()
                .userId(userId)
                .predictionId(predictionId)
                .gameId(gameId)
                .eventType(EventType.UPSET_BONUS)
                .baseScore(baseScore)
                .multiplier(1.0)
                .finalScore(baseScore)
                .streakCount(0)
                .description("이변 예측 성공! UPSET BONUS!")
                .build();
    }

    /**
     * 퍼펙트 데이 보너스 이벤트 생성
     */
    public static ScoreEvent createPerfectDay(Long userId, int gamesWon) {
        int baseScore = EventType.PERFECT_DAY.getDefaultPoints();

        return ScoreEvent.builder()
                .userId(userId)
                .eventType(EventType.PERFECT_DAY)
                .baseScore(baseScore)
                .multiplier(1.0)
                .finalScore(baseScore)
                .streakCount(0)
                .description(String.format("PERFECT DAY! %d경기 전승!", gamesWon))
                .build();
    }

    /**
     * 파워업 보너스 이벤트 생성
     */
    public static ScoreEvent createPowerUpBonus(Long userId, Long predictionId, String gameId,
            int originalScore, double multiplier, String powerupName) {
        int bonusScore = (int) Math.round(originalScore * (multiplier - 1));

        return ScoreEvent.builder()
                .userId(userId)
                .predictionId(predictionId)
                .gameId(gameId)
                .eventType(EventType.POWER_UP_MULTIPLIER)
                .baseScore(originalScore)
                .multiplier(multiplier)
                .finalScore(bonusScore)
                .streakCount(0)
                .description(String.format("%s 사용! x%.1f 배율 적용!", powerupName, multiplier))
                .build();
    }
}
