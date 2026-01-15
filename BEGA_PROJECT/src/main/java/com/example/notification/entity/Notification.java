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

        // 응원게시판 관련
        POST_COMMENT("게시글 댓글"),
        COMMENT_REPLY("댓글 대댓글"),
        POST_LIKE("게시글 좋아요");

        private final String description;

        NotificationType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}