package com.example.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;

/**
 * 사용자 팔로우 관계 엔티티
 * follower가 following을 팔로우하는 관계를 나타냄
 */
@Entity
@Table(name = "user_follow", schema = "security")
@Getter
@Setter
@NoArgsConstructor
public class UserFollow {

    @EmbeddedId
    private Id id = new Id();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("followerId")
    @JoinColumn(name = "follower_id")
    private UserEntity follower;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("followingId")
    @JoinColumn(name = "following_id")
    private UserEntity following;

    /**
     * 새 글 알림 설정
     * true: 팔로우한 유저가 글 쓰면 알림 받음 ("모든 글 알림")
     * false: 피드에만 노출, 알림 없음 ("단순 팔로우") - 기본값
     */
    @Column(name = "notify_new_posts", nullable = false)
    private Boolean notifyNewPosts = false;

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
        private Long followerId;
        private Long followingId;
    }
}
