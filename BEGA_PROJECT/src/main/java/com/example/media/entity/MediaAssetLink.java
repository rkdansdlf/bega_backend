package com.example.media.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "media_asset_links")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaAssetLink implements Persistable<Long> {

    @Id
    @Column(name = "asset_id")
    private Long assetId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "asset_id")
    private MediaAsset asset;

    @Enumerated(EnumType.STRING)
    @Column(name = "domain", nullable = false, length = 32)
    private MediaDomain domain;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private MediaLinkRole role;

    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt;

    @Transient
    @Builder.Default
    private boolean newEntity = true;

    @Override
    public Long getId() {
        return assetId;
    }

    @Override
    @Transient
    public boolean isNew() {
        return newEntity;
    }

    @PrePersist
    void onCreate() {
        if (linkedAt == null) {
            linkedAt = LocalDateTime.now();
        }
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.newEntity = false;
    }
}
