package com.example.leaderboard.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 활성화된 파워업 엔티티
 * 현재 사용 중인 파워업 효과를 추적합니다.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "active_powerups")
@EntityListeners(AuditingEntityListener.class)
public class ActivePowerup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "powerup_type", nullable = false, length = 50)
    private UserPowerup.PowerupType powerupType;

    /** 특정 게임에 적용된 파워업인 경우 */
    @Column(name = "game_id", length = 50)
    private String gameId;

    @CreatedDate
    @Column(name = "activated_at", updatable = false)
    private LocalDateTime activatedAt;

    /** 만료 시간 (null이면 한 번 사용으로 소진) */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** 이미 사용되었는지 여부 */
    @Column(name = "used", nullable = false)
    @Builder.Default
    private Boolean used = false;

    // ============================================
    // HELPER METHODS
    // ============================================

    /**
     * 파워업이 아직 유효한지 확인
     */
    public boolean isActive() {
        if (used) {
            return false;
        }
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) {
            return false;
        }
        return true;
    }

    /**
     * 파워업 사용 완료 처리
     */
    public void markAsUsed() {
        this.used = true;
    }

    /**
     * 정적 팩토리 메서드: 특정 게임용 파워업 활성화
     */
    public static ActivePowerup activateForGame(Long userId, UserPowerup.PowerupType type, String gameId) {
        return ActivePowerup.builder()
                .userId(userId)
                .powerupType(type)
                .gameId(gameId)
                .used(false)
                .build();
    }

    /**
     * 정적 팩토리 메서드: 시간 제한 파워업 활성화
     */
    public static ActivePowerup activateWithExpiry(Long userId, UserPowerup.PowerupType type, LocalDateTime expiresAt) {
        return ActivePowerup.builder()
                .userId(userId)
                .powerupType(type)
                .expiresAt(expiresAt)
                .used(false)
                .build();
    }
}
