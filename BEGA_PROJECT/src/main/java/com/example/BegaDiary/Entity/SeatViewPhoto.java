package com.example.BegaDiary.Entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "seat_view_photo")
@NoArgsConstructor
public class SeatViewPhoto {

    public enum SourceType {
        DIARY_UPLOAD,
        TICKET_SCAN
    }

    public enum ClassificationLabel {
        SEAT_VIEW,
        TICKET,
        OTHER,
        INAPPROPRIATE
    }

    public enum ModerationStatus {
        PENDING,
        APPROVED,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diary_id", nullable = false)
    private BegaDiary diary;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "storage_path", nullable = false, length = 2048)
    private String storagePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private SourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_suggested_label", length = 32)
    private ClassificationLabel aiSuggestedLabel;

    @Column(name = "ai_confidence")
    private Double aiConfidence;

    @Column(name = "ai_reason", length = 1000)
    private String aiReason;

    @Column(name = "user_selected", nullable = false)
    private boolean userSelected;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", length = 32)
    private ModerationStatus moderationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "admin_label", length = 32)
    private ClassificationLabel adminLabel;

    @Column(name = "admin_memo", length = 1000)
    private String adminMemo;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reward_granted", nullable = false)
    private boolean rewardGranted;

    @Column(name = "createdat", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updatedat", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public SeatViewPhoto(
            BegaDiary diary,
            Long userId,
            String storagePath,
            SourceType sourceType,
            ClassificationLabel aiSuggestedLabel,
            Double aiConfidence,
            String aiReason,
            boolean userSelected,
            ModerationStatus moderationStatus,
            ClassificationLabel adminLabel,
            String adminMemo,
            Long reviewedBy,
            LocalDateTime reviewedAt,
            boolean rewardGranted) {
        this.diary = diary;
        this.userId = userId;
        this.storagePath = storagePath;
        this.sourceType = sourceType;
        this.aiSuggestedLabel = aiSuggestedLabel;
        this.aiConfidence = aiConfidence;
        this.aiReason = aiReason;
        this.userSelected = userSelected;
        this.moderationStatus = moderationStatus;
        this.adminLabel = adminLabel;
        this.adminMemo = adminMemo;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
        this.rewardGranted = rewardGranted;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void updateSuggestion(ClassificationLabel label, Double confidence, String reason) {
        this.aiSuggestedLabel = label;
        this.aiConfidence = confidence;
        this.aiReason = reason;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateUserSelection(boolean selected) {
        this.userSelected = selected;
        this.moderationStatus = selected ? ModerationStatus.PENDING : null;
        if (!selected) {
            this.adminLabel = null;
            this.adminMemo = null;
            this.reviewedBy = null;
            this.reviewedAt = null;
        }
        this.updatedAt = LocalDateTime.now();
    }

    public void review(
            ClassificationLabel adminLabel,
            ModerationStatus moderationStatus,
            Long reviewedBy,
            String adminMemo) {
        this.adminLabel = adminLabel;
        this.moderationStatus = moderationStatus;
        this.reviewedBy = reviewedBy;
        this.adminMemo = adminMemo;
        this.reviewedAt = LocalDateTime.now();
        this.updatedAt = this.reviewedAt;
    }

    public void markRewardGranted() {
        this.rewardGranted = true;
        this.updatedAt = LocalDateTime.now();
    }
}
