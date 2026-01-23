package com.example.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;

/**
 * 사용자 차단 관계 엔티티
 * blocker가 blocked를 차단하는 관계를 나타냄
 */
@Entity
@Table(name = "user_block", schema = "security")
@Getter
@Setter
@NoArgsConstructor
public class UserBlock {

    @EmbeddedId
    private Id id = new Id();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("blockerId")
    @JoinColumn(name = "blocker_id")
    private UserEntity blocker;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("blockedId")
    @JoinColumn(name = "blocked_id")
    private UserEntity blocked;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Embeddable
    @EqualsAndHashCode
    public static class Id implements Serializable {
        private Long blockerId;
        private Long blockedId;
    }
}
