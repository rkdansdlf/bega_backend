package com.example.media.service;

import com.example.common.validation.OwnerIdentity;
import com.example.auth.repository.UserRepository;
import com.example.BegaDiary.Repository.SeatViewPhotoRepository;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.cheerboard.storage.repository.PostImageRepository;
import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.strategy.StorageObjectKey;
import com.example.common.exception.ConflictBusinessException;
import com.example.media.entity.MediaAsset;
import com.example.media.entity.MediaAssetStatus;
import com.example.media.entity.MediaDomain;
import com.example.media.repository.MediaAssetLinkRepository;
import com.example.media.repository.MediaAssetRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Reserves a single managed cheer object in the caller's image-removal transaction. */
@Service
@RequiredArgsConstructor
public class CheerIndividualMediaDeletion {
    private final MediaAssetRepository assets;
    private final MediaAssetLinkRepository links;
    private final PostImageRepository images;
    private final UserRepository users;
    private final CheerReportRepo reports;
    private final SeatViewPhotoRepository seatViewPhotos;
    private final StorageConfig storageConfig;
    private final EntityManager entityManager;

    public record Candidate(Long assetId, Long ownerId, String objectKey) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public Candidate lockOwners(String path, Long authorId) {
        MediaAsset asset = assets.findByObjectKey(path).orElseThrow(this::conflict);
        if (asset.getId() == null || asset.getOwnerUserId() == null) throw conflict();
        Candidate candidate = new Candidate(asset.getId(), asset.getOwnerUserId(), path);
        TreeSet<Long> ownerIds = new TreeSet<>();
        ownerIds.add(Objects.requireNonNull(authorId));
        ownerIds.add(candidate.ownerId());
        for (Long ownerId : ownerIds) {
            users.findByIdForWrite(ownerId).orElseThrow(this::conflict);
            if (users.existsByIdAndPendingDeletionTrueAndDeletionScheduledForIsNotNull(ownerId)
                    || (users.existsByIdAndPendingDeletionTrue(ownerId)
                        && !reports.findOpenModerationPostIdsByAuthorId(ownerId).isEmpty())) {
                throw conflict();
            }
        }
        return candidate;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(Candidate candidate, Long postId, Long imageId, String path) {
        if (!Objects.equals(candidate.objectKey(), path)) throw conflict();
        MediaAsset asset = assets.findByIdForUpdate(candidate.assetId()).orElseThrow(this::conflict);
        entityManager.refresh(asset, LockModeType.PESSIMISTIC_WRITE);
        if (!Objects.equals(asset.getObjectKey(), path)
                || !OwnerIdentity.isSameOwner(asset.getOwnerUserId(), candidate.ownerId())
                || asset.getDomain() != MediaDomain.CHEER || asset.getStatus() != MediaAssetStatus.READY
                || links.countCheerPostLink(asset.getId(), postId) != 1
                || hasSeatViewReference(path)
                || images.countOtherImageReferences(imageId, path) != 0) {
            throw conflict();
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lease = now.plusMinutes(15);
        if (asset.getUploadExpiresAt() != null && asset.getUploadExpiresAt().plusMinutes(15).isAfter(lease)) {
            lease = asset.getUploadExpiresAt().plusMinutes(15);
        }
        if (asset.getFinalizeLeaseUntil() != null && asset.getFinalizeLeaseUntil().plusMinutes(15).isAfter(lease)) {
            lease = asset.getFinalizeLeaseUntil().plusMinutes(15);
        }
        links.deleteByAssetId(asset.getId());
        asset.setStatus(MediaAssetStatus.DELETED);
        asset.setFinalizeToken(UUID.randomUUID().toString());
        asset.setFinalizeLeaseUntil(lease);
        asset.setFinalizeCleanupVerifiedAt(null);
    }

    private boolean hasSeatViewReference(String path) {
        String bucket = storageConfig.getCheerBucket();
        if (bucket == null || bucket.isBlank()) throw conflict();
        var aliases = StorageObjectKey.aliases(bucket, path);
        String shortest = aliases.stream().min(java.util.Comparator.comparingInt(String::length)).orElseThrow();
        return seatViewPhotos.existsByTrimmedStoragePathIn(aliases)
                || seatViewPhotos.existsPossiblyReferencingStoragePath(shortest);
    }

    private ConflictBusinessException conflict() {
        return new ConflictBusinessException("CHEER_MEDIA_DELETE_CONFLICT", "이미지 삭제 상태를 확인한 뒤 다시 시도해주세요.");
    }
}
