package com.example.media.entity;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "media_assets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "domain", nullable = false, length = 32)
    private MediaDomain domain;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MediaAssetStatus status;

    @Column(name = "original_file_name", length = 512)
    private String originalFileName;

    @Column(name = "declared_content_type", nullable = false, length = 128)
    private String declaredContentType;

    @Column(name = "declared_bytes", nullable = false)
    private Long declaredBytes;

    @Column(name = "declared_width", nullable = false)
    private Integer declaredWidth;

    @Column(name = "declared_height", nullable = false)
    private Integer declaredHeight;

    @Column(name = "staging_object_key", nullable = false, length = 2048)
    private String stagingObjectKey;

    @Column(name = "object_key", length = 2048)
    private String objectKey;

    @Column(name = "stored_content_type", length = 128)
    private String storedContentType;

    @Column(name = "stored_bytes")
    private Long storedBytes;

    @Column(name = "stored_width")
    private Integer storedWidth;

    @Column(name = "stored_height")
    private Integer storedHeight;

    @Column(name = "upload_expires_at", nullable = false)
    private LocalDateTime uploadExpiresAt;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "derived_from_asset_id")
    private MediaAsset derivedFrom;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void markReady(String objectKey, String storedContentType, long storedBytes, int storedWidth, int storedHeight) {
        this.status = MediaAssetStatus.READY;
        this.objectKey = objectKey;
        this.storedContentType = storedContentType;
        this.storedBytes = storedBytes;
        this.storedWidth = storedWidth;
        this.storedHeight = storedHeight;
        this.finalizedAt = LocalDateTime.now();
    }

    public void markOrphaned() {
        this.status = MediaAssetStatus.ORPHANED;
    }

    public void markDeleted() {
        this.status = MediaAssetStatus.DELETED;
    }
}
