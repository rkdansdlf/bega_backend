package com.example.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 관리자 작업 감사 로그 엔티티
 * 권한 변경, 사용자 삭제 등 관리자 작업을 기록
 */
@Entity
@Table(name = "admin_audit_logs")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 작업을 수행한 관리자 ID
     */
    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    /**
     * 작업 대상 사용자 ID
     */
    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    /**
     * 수행된 작업 유형
     */
    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private AuditAction action;

    /**
     * 변경 전 값 (예: "ROLE_USER")
     */
    @Column(name = "old_value", length = 100)
    private String oldValue;

    /**
     * 변경 후 값 (예: "ROLE_ADMIN")
     */
    @Column(name = "new_value", length = 100)
    private String newValue;

    /**
     * 추가 설명 또는 사유
     */
    @Column(length = 500)
    private String description;

    /**
     * 작업 수행 시각
     */
    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    /**
     * 감사 작업 유형 Enum
     */
    public enum AuditAction {
        PROMOTE_TO_ADMIN("관리자로 승격"),
        DEMOTE_TO_USER("일반 사용자로 강등"),
        DELETE_USER("사용자 삭제"),
        DELETE_POST("게시글 삭제"),
        DELETE_MATE("메이트 모임 삭제");

        private final String description;

        AuditAction(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
