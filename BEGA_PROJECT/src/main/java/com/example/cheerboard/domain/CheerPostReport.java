package com.example.cheerboard.domain;

import com.example.auth.entity.UserEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "cheer_post_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheerPostReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private CheerPost post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private UserEntity reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportReason reason;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 24, nullable = false)
    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "admin_action", length = 30)
    private AdminAction adminAction;

    @Column(name = "admin_memo", length = 1000)
    private String adminMemo;

    @Column(name = "handled_by")
    private Long handledBy;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @Column(name = "evidence_url", length = 1024)
    private String evidenceUrl;

    @Column(name = "requested_action", length = 64)
    private String requestedAction;

    @Enumerated(EnumType.STRING)
    @Column(name = "appeal_status", length = 24)
    @Builder.Default
    private AppealStatus appealStatus = AppealStatus.NONE;

    @Column(name = "appeal_reason", length = 1200)
    private String appealReason;

    @Column(name = "appeal_count", nullable = false)
    @Builder.Default
    private Integer appealCount = 0;

    @Column(name = "createdat", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public enum ReportStatus {
        PENDING,
        IN_REVIEW,
        RESOLVED,
        CLOSED
    }

    public enum AdminAction {
        TAKE_DOWN,
        REQUIRE_MODIFICATION,
        WARNING,
        DISMISS,
        RESTORE
    }

    public enum AppealStatus {
        NONE,
        REQUESTED,
        REVIEWING,
        ACCEPTED,
        REJECTED
    }
}
