package com.example.leaderboard.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 사용자 파워업 인벤토리 엔티티
 * 각 파워업 아이템의 보유 수량을 추적합니다.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "user_powerups", uniqueConstraints = @UniqueConstraint(columnNames = { "user_id", "powerup_type" }))
@EntityListeners(AuditingEntityListener.class)
public class UserPowerup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "powerup_type", nullable = false, length = 50)
    private PowerupType powerupType;

    @Column(name = "quantity", nullable = false)
    @Builder.Default
    private Integer quantity = 0;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ============================================
    // POWERUP TYPE ENUM
    // ============================================
    public enum PowerupType {
        /** 매직 배트: 다음 예측 점수 2배 */
        MAGIC_BAT("매직 배트", "다음 예측 점수 2배!", 2.0, "🏏"),

        /** 골든 글러브: 연승 보호 (1회 실패 무효) */
        GOLDEN_GLOVE("골든 글러브", "연승 보호 (1회 실패 무효)", 0.0, "🧤"),

        /** 스카우터: 다른 유저 투표 비율 미리보기 */
        SCOUTER("스카우터", "다른 유저 투표 비율 미리보기", 0.0, "🔭");

        private final String koreanName;
        private final String description;
        private final double multiplier;
        private final String icon;

        PowerupType(String koreanName, String description, double multiplier, String icon) {
            this.koreanName = koreanName;
            this.description = description;
            this.multiplier = multiplier;
            this.icon = icon;
        }

        public String getKoreanName() {
            return koreanName;
        }

        public String getDescription() {
            return description;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public String getIcon() {
            return icon;
        }
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    /**
     * 파워업 사용 (수량 감소)
     * 
     * @return 사용 가능하면 true, 불가능하면 false
     */
    public boolean use() {
        if (this.quantity == null || this.quantity <= 0) {
            return false;
        }
        this.quantity--;
        return true;
    }

    /**
     * 파워업 추가
     * 
     * @param amount 추가 수량
     */
    public void add(int amount) {
        this.quantity = (this.quantity == null ? 0 : this.quantity) + amount;
    }

    /**
     * 파워업 보유 여부 확인
     */
    public boolean hasAny() {
        return this.quantity != null && this.quantity > 0;
    }

    /**
     * 정적 팩토리 메서드: 새 UserPowerup 생성
     */
    public static UserPowerup create(Long userId, PowerupType type, int quantity) {
        return UserPowerup.builder()
                .userId(userId)
                .powerupType(type)
                .quantity(quantity)
                .build();
    }
}
