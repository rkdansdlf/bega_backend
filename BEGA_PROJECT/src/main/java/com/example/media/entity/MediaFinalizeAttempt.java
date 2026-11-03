package com.example.media.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Durable reference to every output key written by one media finalization attempt. */
@Entity
@Table(name = "media_finalize_attempts")
@Getter
@NoArgsConstructor
public class MediaFinalizeAttempt {

    @Id
    @Column(name = "attempt_id", length = 36, nullable = false)
    private String attemptId;

    @Column(name = "media_asset_id", nullable = false)
    private Long mediaAssetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_domain", length = 30, nullable = false)
    private MediaDomain domain;

    @Column(name = "bucket_name", length = 255, nullable = false)
    private String bucketName;

    @Column(name = "object_key", length = 2048)
    private String objectKey;

    @Column(name = "feed_object_key", length = 2048)
    private String feedObjectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 32, nullable = false)
    private MediaFinalizeAttemptStatus status;

    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @Column(name = "next_cleanup_at")
    private LocalDateTime nextCleanupAt;

    @Column(name = "cleanup_token", length = 36)
    private String cleanupToken;

    @Column(name = "cleanup_lease_until")
    private LocalDateTime cleanupLeaseUntil;

    @Column(name = "cleanup_attempt_count", nullable = false)
    private Integer cleanupAttemptCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public MediaFinalizeAttempt(
            String attemptId,
            Long mediaAssetId,
            MediaDomain domain,
            String bucketName,
            LocalDateTime leaseExpiresAt,
            LocalDateTime now) {
        if (attemptId == null || mediaAssetId == null || domain == null || bucketName == null || now == null) {
            throw new IllegalArgumentException("Finalize attempt identity and storage scope are required.");
        }
        this.attemptId = attemptId;
        this.mediaAssetId = mediaAssetId;
        this.domain = domain;
        this.bucketName = bucketName;
        this.status = MediaFinalizeAttemptStatus.ACTIVE;
        this.leaseExpiresAt = leaseExpiresAt;
        this.cleanupAttemptCount = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }
}
