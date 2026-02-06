package com.example.notification.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId; // 알림을 받을 사용자 ID

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private NotificationType type; // 알림 타입

    @Column(nullable = false, length = 200)
    private String title; // 알림 제목

    @Column(nullable = false, length = 500)
    private String message; // 알림 내용

    @Column(name = "related_id")
    private Long relatedId; // 관련 ID (파티 ID 또는 신청 ID)

    @Column(name = "is_read", nullable = false)
    private Boolean isRead; // 읽음 여부

    @Column(name = "createdat", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (isRead == null) {
            isRead = false;
        }
    }

    public enum NotificationType {
        // 메이트 관련 알림
        APPLICATION_RECEIVED("신청 접수"),
        APPLICATION_APPROVED("신청 승인"),
        APPLICATION_REJECTED("신청 거절"),
        PARTY_EXPIRED("파티 모집 만료"),
        PARTY_AUTO_COMPLETED("파티 자동 완료"),
        GAME_TOMORROW_REMINDER("내일 경기 알림"),
        GAME_DAY_REMINDER("오늘 경기 알림"),
        HOST_RESPONSE_NUDGE("호스트 응답 촉구"),
        REVIEW_REQUEST("리뷰 요청"),
        PARTY_CANCELLED_HOST_DELETED("파티 취소 (호스트 탈퇴)"),
        PARTY_PARTICIPANT_LEFT("참여자 탈퇴"),

        // 응원게시판 관련
        POST_COMMENT("게시글 댓글"),
        COMMENT_REPLY("댓글 대댓글"),
        POST_LIKE("게시글 좋아요"),
        POST_REPOST("게시글 리포스트"),

        // 팔로우 관련 알림
        NEW_FOLLOWER("새 팔로워"),
        FOLLOWING_NEW_POST("새로운 게시글");

        private final String description;

        NotificationType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}