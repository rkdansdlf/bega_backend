package com.example.cheerboard.retention;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 신고된 게시글 이미지 한 장의 보관 상태. 저장소 경로와 기한만 담고 개인 식별 정보는 두지 않는다. */
@Entity
@Table(name = "report_evidence_media_retention")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ReportEvidenceMediaRetention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_image_id", nullable = false)
    private Long postImageId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    /** 유일성은 DB의 부분 유니크 인덱스가 보장한다. 복구되어 끝난 행(RESOLVED_RESTORED)은 같은 객체를 다시 등록하지 못하게 막지 않는다. */
    @Column(name = "storage_path", nullable = false, length = 512)
    private String storagePath;

    /** 계정 최초 삭제 요청일. 이 값이 없으면 자동 만료 대상이 아니다. */
    @Column(name = "base_requested_at")
    private LocalDateTime baseRequestedAt;

    /** 등록 시점의 삭제 주기 시작일. 기준일 계산에 쓴 값을 그대로 남겨 두어, 이후 계정 상태가 바뀌어도 설명할 수 있게 한다. */
    @Column(name = "cycle_started_at")
    private LocalDateTime cycleStartedAt;

    /** 긴급 검토 유예를 처음 시작한 시각. 한 번 값이 생기면 같은 행은 다시 유예를 받지 못한다. */
    @Column(name = "urgent_review_started_at")
    private LocalDateTime urgentReviewStartedAt;

    /** 긴급 검토 유예가 끝나는 시각. 이 시각까지 결정이 없으면 삭제 대상이 된다. */
    @Column(name = "urgent_review_due_at")
    private LocalDateTime urgentReviewDueAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "retain_until")
    private LocalDateTime retainUntil;

    @Column(name = "hard_limit_at")
    private LocalDateTime hardLimitAt;

    @Column(name = "extension_until")
    private LocalDateTime extensionUntil;

    @Column(name = "extension_case_ref", length = 64)
    private String extensionCaseRef;

    @Column(name = "extension_approved_by", length = 64)
    private String extensionApprovedBy;

    @Column(name = "legal_basis", length = 512)
    private String legalBasis;

    @Column(name = "legal_review_due_at")
    private LocalDateTime legalReviewDueAt;

    @Column(name = "legal_hold_approved_by", length = 64)
    private String legalHoldApprovedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ReportEvidenceMediaStatus status;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    /** 삭제 점유 시각. 객체 삭제 호출 전에 DB에 찍고, 호출 전후로 DB 행 잠금은 잡지 않는다. 유효 시간이 지나면 풀린다. */
    @Column(name = "delete_claimed_at")
    private LocalDateTime deleteClaimedAt;

    /** First committed deletion claim; unlike the retry lease this fence never expires. */
    @Column(name = "delete_started_at")
    private LocalDateTime deleteStartedAt;

    @Column(name = "delete_claim_token", length = 36)
    private String deleteClaimToken;

    /** 삭제 대기로 넘어간 이유. 삭제 이력에 그대로 남긴다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "delete_reason", length = 32)
    private ReportEvidenceDeleteReason deleteReason;

    /** 운영자가 삭제를 승인하면서 지정한 사건 번호. 이 사건이 유일한 미결 사건인 동안에만 승인이 유효하다. */
    @Column(name = "release_case_ref", length = 64)
    private String releaseCaseRef;

    @Column(name = "last_error", length = 512)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** 낙관적 잠금 버전. 만료 판정과 보존 근거·연장 기록이 동시에 바뀌면 한쪽 쓰기가 실패하도록 한다. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static ReportEvidenceMediaRetention activeWithBase(Long postImageId, Long postId, Long ownerUserId,
                                                              String storagePath, LocalDateTime baseRequestedAt,
                                                              LocalDateTime cycleStartedAt, LocalDateTime now) {
        return ReportEvidenceMediaRetention.builder()
                .postImageId(postImageId)
                .postId(postId)
                .ownerUserId(ownerUserId)
                .storagePath(storagePath)
                .baseRequestedAt(baseRequestedAt)
                .cycleStartedAt(cycleStartedAt)
                .retainUntil(ReportEvidenceRetentionPolicy.defaultUntil(baseRequestedAt))
                .hardLimitAt(ReportEvidenceRetentionPolicy.hardLimit(baseRequestedAt))
                .status(ReportEvidenceMediaStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /** 긴급 검토 유예를 시작한다. 이미 시작한 행이면 아무 일도 하지 않는다(유예는 한 번만). */
    public boolean markUrgentReview(LocalDateTime now) {
        if (this.urgentReviewStartedAt != null) {
            return false;
        }
        this.status = ReportEvidenceMediaStatus.URGENT_REVIEW;
        this.urgentReviewStartedAt = now;
        this.urgentReviewDueAt = ReportEvidenceRetentionPolicy.urgentReviewDueAt(now, this.hardLimitAt);
        this.nextAttemptAt = null;
        this.updatedAt = now;
        return true;
    }

    public void markResolvedRestored(LocalDateTime now) {
        this.status = ReportEvidenceMediaStatus.RESOLVED_RESTORED;
        this.resolvedAt = now;
        this.nextAttemptAt = null;
        this.updatedAt = now;
    }

    public static ReportEvidenceMediaRetention reviewRequired(Long postImageId, Long postId, Long ownerUserId,
                                                              String storagePath, LocalDateTime now) {
        return ReportEvidenceMediaRetention.builder()
                .postImageId(postImageId)
                .postId(postId)
                .ownerUserId(ownerUserId)
                .storagePath(storagePath)
                .status(ReportEvidenceMediaStatus.REVIEW_REQUIRED)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void markDeletePending(ReportEvidenceDeleteReason reason, LocalDateTime now) {
        this.status = ReportEvidenceMediaStatus.DELETE_PENDING;
        this.deleteReason = reason;
        this.nextAttemptAt = null;
        this.updatedAt = now;
    }

    /** 검토 대기 기록을 운영자 승인으로 삭제 대기에 넣는다. 보존 근거와 점유 검사는 삭제 시점에 다시 한다. */
    public void releaseForDeletion(String caseRef, LocalDateTime now) {
        this.status = ReportEvidenceMediaStatus.DELETE_PENDING;
        this.deleteReason = ReportEvidenceDeleteReason.OPERATOR_RELEASED;
        this.releaseCaseRef = caseRef;
        this.nextAttemptAt = null;
        this.updatedAt = now;
    }

    /** 삭제 호출을 시작하기 전에 점유를 잡는다. 점유 중에는 다른 실행이 같은 행을 집지 않고, 보존 변경도 거부된다. */
    public void claimForDelete(LocalDateTime now) {
        if (deleteStartedAt == null) this.deleteStartedAt = now;
        this.deleteClaimedAt = now;
        this.deleteClaimToken = java.util.UUID.randomUUID().toString();
        this.updatedAt = now;
    }

    public boolean isDeleteClaimed(LocalDateTime now) {
        return deleteClaimedAt != null && now.isBefore(deleteClaimedAt.plus(ReportEvidenceRetentionPolicy.DELETE_CLAIM_LEASE));
    }

    public boolean hasDeletionStarted() {
        return deleteStartedAt != null;
    }

    /** 저장소 호출이 끝나서 실행 중인 호출이 없을 때만 점유를 푼다. 제한 시간을 넘긴 호출에는 쓰지 않는다. */
    public void releaseDeleteClaim(LocalDateTime now) {
        this.deleteClaimedAt = null;
        this.deleteClaimToken = null;
        this.updatedAt = now;
    }

    /** 같은 객체를 다른 유효한 행이 아직 보호하고 있을 때 삭제를 하루 미룬다. 실패로 세지 않는다. */
    public void deferDelete(LocalDateTime now) {
        this.nextAttemptAt = now.plus(ReportEvidenceRetentionPolicy.DELETE_DEFERRAL);
        this.updatedAt = now;
    }

    /** 삭제 대기 중인 행을 다시 활성 상태로 돌린다. 새 보존 근거가 걸렸을 때 쓴다. */
    public void reactivate(LocalDateTime now) {
        this.status = ReportEvidenceMediaStatus.ACTIVE;
        this.nextAttemptAt = null;
        this.updatedAt = now;
    }

    public void markReviewRequired(LocalDateTime now) {
        this.status = ReportEvidenceMediaStatus.REVIEW_REQUIRED;
        this.updatedAt = now;
    }

    public void markDeleted(LocalDateTime now) {
        this.status = ReportEvidenceMediaStatus.DELETED;
        this.deletedAt = now;
        this.deleteClaimedAt = null;
        this.deleteClaimToken = null;
        this.nextAttemptAt = null;
        this.lastError = null;
        this.updatedAt = now;
    }

    public void recordDeleteFailure(String error, LocalDateTime now) {
        this.attemptCount++;
        this.lastError = truncate(error);
        this.nextAttemptAt = ReportEvidenceRetentionPolicy.nextAttemptAfter(this.attemptCount, now);
        this.updatedAt = now;
    }

    public void approveExtension(LocalDateTime until, String caseRef, String approvedBy, LocalDateTime now) {
        this.extensionUntil = until;
        this.extensionCaseRef = caseRef;
        this.extensionApprovedBy = approvedBy;
        this.updatedAt = now;
    }

    public void placeLegalHold(String basis, LocalDateTime reviewDueAt, String approvedBy, LocalDateTime now) {
        this.legalBasis = basis;
        this.legalReviewDueAt = reviewDueAt;
        this.legalHoldApprovedBy = truncateTo64(approvedBy);
        this.updatedAt = now;
    }

    private static String truncateTo64(String text) {
        return text == null || text.length() <= 64 ? text : text.substring(0, 64);
    }

    private static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= 512 ? text : text.substring(0, 512);
    }
}
