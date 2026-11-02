package com.example.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Private reconciliation record, independent of the original post and image rows. */
@Entity
@Table(name = "legacy_post_image_holds")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LegacyPostImageHold {
    public enum Status { UNRESOLVED }

    @Id
    @Column(name = "post_image_id", nullable = false, updatable = false)
    private Long postImageId;

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private Long ownerUserId;

    @Column(name = "post_id", nullable = false, updatable = false)
    private Long postId;

    @Column(name = "storage_path", nullable = false, length = 2048, updatable = false)
    private String storagePath;

    @Column(name = "logical_bucket", length = 255, updatable = false)
    private String logicalBucket;

    @Column(name = "report_protected", nullable = false, updatable = false)
    private boolean reportProtected;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24, updatable = false)
    @Builder.Default
    private Status status = Status.UNRESOLVED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
