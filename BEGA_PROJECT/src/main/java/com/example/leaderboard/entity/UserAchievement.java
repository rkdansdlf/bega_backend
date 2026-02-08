package com.example.leaderboard.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 사용자가 획득한 업적 엔티티 (Junction Table)
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "user_achievements", uniqueConstraints = @UniqueConstraint(columnNames = { "user_id", "achievement_id" }))
@EntityListeners(AuditingEntityListener.class)
public class UserAchievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "achievement_id", nullable = false)
    private Achievement achievement;

    @CreatedDate
    @Column(name = "earned_at", updatable = false)
    private LocalDateTime earnedAt;

    /**
     * 정적 팩토리 메서드
     */
    public static UserAchievement create(Long userId, Achievement achievement) {
        return UserAchievement.builder()
                .userId(userId)
                .achievement(achievement)
                .build();
    }
}
