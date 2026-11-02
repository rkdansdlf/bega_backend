package com.example.media.entity;

import com.example.media.service.PhysicalObjectLifecycle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Read-only snapshot; locking and identity verification are provided by the repository. */
@Entity
@Table(name = "physical_media_objects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhysicalMediaObject {
    @Id
    @Column(name = "object_hash", length = 64, nullable = false, updatable = false)
    private String objectHash;

    @Column(name = "physical_bucket", length = 255, nullable = false, updatable = false)
    private String physicalBucket;

    @Column(name = "object_key", length = 2048, nullable = false, updatable = false)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 24, nullable = false, updatable = false)
    private PhysicalObjectLifecycle.State state;

    @Column(name = "audit_generation", nullable = false, updatable = false)
    private long auditGeneration;

    @Column(name = "reference_revision", nullable = false, updatable = false)
    private long referenceRevision;

    @Column(name = "claim_generation", nullable = false, updatable = false)
    private long claimGeneration;

    @Column(name = "deletion_requested", nullable = false, updatable = false)
    private boolean deletionRequested;

    @Column(name = "claim_token", length = 36, updatable = false)
    private String claimToken;

    @Column(name = "lease_until", updatable = false)
    private LocalDateTime leaseUntil;

    @Column(name = "retry_at", updatable = false)
    private LocalDateTime retryAt;

    @Column(name = "first_absent_at", updatable = false)
    private LocalDateTime firstAbsentAt;

    @Column(name = "first_absence_generation", nullable = false, updatable = false)
    private long firstAbsenceGeneration;

    @Column(name = "second_absent_at", updatable = false)
    private LocalDateTime secondAbsentAt;

    @Column(name = "attempt_count", nullable = false, updatable = false)
    private int attemptCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, updatable = false)
    private LocalDateTime updatedAt;
}
