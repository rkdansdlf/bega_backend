package com.example.media.service;

import com.example.common.exception.ConflictBusinessException;
import com.example.media.entity.MediaAsset;
import com.example.media.entity.MediaAssetStatus;
import com.example.media.entity.MediaDomain;
import com.example.media.repository.MediaAssetLinkRepository;
import com.example.media.repository.MediaAssetRepository;
import com.example.cheerboard.storage.repository.PostImageRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Prepares managed images while the caller holds the post's purge claim in the same transaction. */
@Service
@RequiredArgsConstructor
public class CheerMediaPurgeReservation {
    private final MediaAssetRepository assets;
    private final MediaAssetLinkRepository links;
    private final PostImageRepository images;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Long> ownerIds(Long postId) {
        return links.findCheerAssetOwnerIds(postId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(Long postId, Collection<String> imagePaths) {
        lockAndValidate(postId, imagePaths, false).forEach(asset -> asset.setStatus(MediaAssetStatus.CHEER_PURGE_PENDING));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void finish(Long postId, Collection<String> imagePaths) {
        LocalDateTime now = LocalDateTime.now();
        for (MediaAsset asset : lockAndValidate(postId, imagePaths, true)) {
            LocalDateTime leaseUntil = now.plusMinutes(15);
            if (asset.getUploadExpiresAt() != null && asset.getUploadExpiresAt().plusMinutes(15).isAfter(leaseUntil)) {
                leaseUntil = asset.getUploadExpiresAt().plusMinutes(15);
            }
            if (asset.getFinalizeLeaseUntil() != null && asset.getFinalizeLeaseUntil().plusMinutes(15).isAfter(leaseUntil)) {
                leaseUntil = asset.getFinalizeLeaseUntil().plusMinutes(15);
            }
            asset.setStatus(MediaAssetStatus.DELETED);
            asset.setFinalizeToken(UUID.randomUUID().toString());
            asset.setFinalizeLeaseUntil(leaseUntil);
            asset.setFinalizeCleanupVerifiedAt(null);
        }
    }

    private Collection<MediaAsset> lockAndValidate(Long postId, Collection<String> imagePaths, boolean finishing) {
        List<String> paths = imagePaths.stream().filter(Objects::nonNull).map(String::strip).toList();
        if (paths.stream().anyMatch(String::isBlank)) throw invalidAsset();
        if (paths.stream().anyMatch(path -> path.startsWith("media/") && !MediaDomain.CHEER.isManagedPath(path))) {
            throw invalidAsset();
        }
        List<String> distinctPaths = paths.stream().distinct().toList();
        entityManager.flush();
        java.util.Set<Long> linkedIds = new java.util.TreeSet<>(links.findCheerAssetIds(postId));
        java.util.Set<Long> assetIds = new java.util.TreeSet<>(linkedIds);
        if (!distinctPaths.isEmpty()) assetIds.addAll(assets.findIdsByObjectKeyInOrderById(distinctPaths));
        Map<String, MediaAsset> lockedByKey = new LinkedHashMap<>();
        for (Long id : assetIds) {
            MediaAsset asset = assets.findByIdForUpdate(id).orElseThrow(this::invalidAsset);
            // A caller may already have read the entity before acquiring this lock.
            entityManager.refresh(asset, LockModeType.PESSIMISTIC_WRITE);
            if (lockedByKey.put(asset.getObjectKey(), asset) != null) {
                throw invalidAsset();
            }
        }
        for (String key : distinctPaths) {
            MediaAsset asset = lockedByKey.get(key);
            if (asset == null && MediaDomain.CHEER.isManagedPath(key)) throw invalidAsset();
        }
        for (MediaAsset asset : lockedByKey.values()) {
            if (asset.getObjectKey() == null || asset.getObjectKey().isBlank()
                    || (asset.getObjectKey().startsWith("media/") && !MediaDomain.CHEER.isManagedPath(asset.getObjectKey()))
                    || asset.getOwnerUserId() == null || asset.getDomain() != MediaDomain.CHEER
                    || (finishing ? asset.getStatus() != MediaAssetStatus.CHEER_PURGE_PENDING
                        : asset.getStatus() != MediaAssetStatus.READY
                            && asset.getStatus() != MediaAssetStatus.CHEER_PURGE_PENDING)) {
                throw invalidAsset();
            }
            if (links.countCheerPostLink(asset.getId(), postId) != 1) throw invalidAsset();
        }
        if (!linkedIds.equals(new java.util.TreeSet<>(links.findCheerAssetIds(postId)))
                || !linkedIds.equals(new java.util.TreeSet<>(assetIds))) {
            throw invalidAsset();
        }
        java.util.Set<String> allPaths = new java.util.LinkedHashSet<>(distinctPaths);
        allPaths.addAll(lockedByKey.keySet());
        if (!allPaths.isEmpty() && images.countOtherPostReferences(postId, allPaths) != 0) throw invalidAsset();
        return lockedByKey.values();
    }

    private ConflictBusinessException invalidAsset() {
        return new ConflictBusinessException("CHEER_MEDIA_PURGE_CONFLICT", "게시글 이미지 연결 상태를 확인한 뒤 다시 시도해주세요.");
    }
}
