package com.example.leaderboard.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 업적/뱃지 정의 엔티티
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "achievements")
@EntityListeners(AuditingEntityListener.class)
public class Achievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name_ko", nullable = false, length = 100)
    private String nameKo;

    @Column(name = "name_en", length = 100)
    private String nameEn;

    @Column(name = "description_ko", length = 500)
    private String descriptionKo;

    @Column(name = "description_en", length = 500)
    private String descriptionEn;

    @Column(name = "icon_url", length = 500)
    private String iconUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "rarity", nullable = false, length = 20)
    @Builder.Default
    private Rarity rarity = Rarity.COMMON;

    @Column(name = "points_required")
    @Builder.Default
    private Long pointsRequired = 0L;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    // ============================================
    // RARITY ENUM
    // ============================================
    public enum Rarity {
        COMMON("일반", "#8a8a8a"),
        RARE("희귀", "#4a90d9"),
        EPIC("에픽", "#a855f7"),
        LEGENDARY("전설", "#ffd700");

        private final String koreanName;
        private final String color;

        Rarity(String koreanName, String color) {
            this.koreanName = koreanName;
            this.color = color;
        }

        public String getKoreanName() {
            return koreanName;
        }

        public String getColor() {
            return color;
        }
    }

    // ============================================
    // PREDEFINED ACHIEVEMENT CODES
    // ============================================
    public static final String FIRST_PREDICTION = "FIRST_PREDICTION";
    public static final String STREAK_3 = "STREAK_3";
    public static final String STREAK_5 = "STREAK_5";
    public static final String STREAK_7 = "STREAK_7";
    public static final String STREAK_10 = "STREAK_10";
    public static final String PERFECT_DAY = "PERFECT_DAY";
    public static final String UPSET_MASTER = "UPSET_MASTER";
    public static final String LEVEL_10 = "LEVEL_10";
    public static final String LEVEL_30 = "LEVEL_30";
    public static final String LEVEL_60 = "LEVEL_60";
    public static final String HALL_OF_FAME = "HALL_OF_FAME";
    public static final String SCORE_100K = "SCORE_100K";
    public static final String SCORE_1M = "SCORE_1M";
}
