package com.example.cheerboard.retention;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 보존 기록의 변경 이력. 추가만 가능하다: 저장소에는 추가 메서드만 두고, DB 트리거가 수정과 삭제를 거부한다.
 * 연장과 법적 보존은 이전 값, 이전 승인자, 이전 근거를 함께 남겨서 두 번째 결정이 첫 번째 기록을 지우지 않게 한다.
 */
@Entity
@Table(name = "report_evidence_media_retention_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ReportEvidenceMediaRetentionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "retention_id", nullable = false)
    private Long retentionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", length = 32)
    private ReportEvidenceDeleteReason reason;

    /** 관리자 계정 번호이거나 시스템 작업이면 SYSTEM. */
    @Column(name = "actor", nullable = false, length = 64)
    private String actor;

    @Column(name = "case_ref", length = 64)
    private String caseRef;

    @Column(name = "previous_value", length = 100)
    private String previousValue;

    @Column(name = "new_value", length = 100)
    private String newValue;

    @Column(name = "basis_text", length = 512)
    private String basisText;

    @Column(name = "previous_basis", length = 512)
    private String previousBasis;

    @Column(name = "previous_actor", length = 64)
    private String previousActor;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public enum EventType {
        REGISTERED,
        REVIEW_REQUIRED,
        URGENT_REVIEW_STARTED,
        DELETE_PENDING,
        RESOLVED_RESTORED,
        EXTENSION_APPROVED,
        LEGAL_HOLD_PLACED,
        RELEASED_FOR_DELETION,
        DELETED
    }
}
