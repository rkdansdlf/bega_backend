package com.example.media.service;

import com.example.common.exception.BadRequestBusinessException;
import com.example.media.entity.MediaAsset;
import com.example.media.entity.MediaAssetLink;
import com.example.media.entity.MediaAssetStatus;
import com.example.media.entity.MediaDomain;
import com.example.media.entity.MediaLinkRole;
import com.example.media.repository.MediaAssetLinkRepository;
import com.example.media.repository.MediaAssetRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MediaLinkService {

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaAssetLinkRepository mediaAssetLinkRepository;
    private final EntityManager entityManager;

    @Transactional
    public ProfileLinkResult syncProfileLinks(Long userId, String profileKey) {
        if (profileKey != null && !profileKey.isBlank()) {
            resolveReadyAssets(userId, MediaDomain.PROFILE, List.of(profileKey));
        }
        entityManager.flush();
        Set<Long> lockedIds = new java.util.TreeSet<>();
        mediaAssetLinkRepository.findByDomainAndEntityId(MediaDomain.PROFILE, userId)
                .forEach(link -> lockedIds.add(link.getAssetId()));
        List<String> keys = profileKey == null ? List.of() : normalizeManagedKeys(MediaDomain.PROFILE, List.of(profileKey));
        if (!keys.isEmpty()) {
            List<Long> primaryIds = mediaAssetRepository.findIdsByObjectKeyInOrderById(keys);
            lockedIds.addAll(primaryIds);
            for (Long primaryId : primaryIds) {
                mediaAssetRepository.findById(primaryId).map(MediaAsset::getDerivedFrom)
                        .map(MediaAsset::getId).ifPresent(lockedIds::add);
                mediaAssetRepository.findByDerivedFrom_Id(primaryId).map(MediaAsset::getId).ifPresent(lockedIds::add);
            }
        }
        lockAssetIds(lockedIds);
        List<MediaAssetLink> existingLinks = mediaAssetLinkRepository.findByDomainAndEntityId(MediaDomain.PROFILE, userId);
        if (existingLinks.stream().anyMatch(link -> !lockedIds.contains(link.getAssetId()))) {
            throw new BadRequestBusinessException("MEDIA_ASSET_NOT_READY", "이미지 상태가 변경되었습니다. 다시 시도해주세요.");
        }
        Map<Long, MediaAssetLink> existingByAssetId = new LinkedHashMap<>();
        for (MediaAssetLink link : existingLinks) {
            existingByAssetId.put(link.getAssetId(), link);
        }

        if (profileKey == null || profileKey.isBlank()) {
            for (MediaAssetLink link : existingLinks) {
                mediaAssetLinkRepository.delete(link);
            }
            return new ProfileLinkResult(null, null);
        }

        Map<String, MediaAsset> readyAssets = resolveReadyAssets(userId, MediaDomain.PROFILE, List.of(profileKey));
        MediaAsset primaryAsset = readyAssets.get(profileKey);
        if (primaryAsset != null && !lockedIds.contains(primaryAsset.getId())) {
            throw new BadRequestBusinessException("MEDIA_ASSET_NOT_READY", "이미지 상태가 변경되었습니다. 다시 시도해주세요.");
        }
        if (primaryAsset != null && primaryAsset.getDerivedFrom() != null
                && (!lockedIds.contains(primaryAsset.getDerivedFrom().getId())
                    || primaryAsset.getDerivedFrom().getStatus() != MediaAssetStatus.READY
                    || primaryAsset.getDerivedFrom().getDomain() != MediaDomain.PROFILE
                    || !Objects.equals(primaryAsset.getDerivedFrom().getOwnerUserId(), userId))) {
            throw new BadRequestBusinessException("MEDIA_ASSET_NOT_READY", "이미지 상태가 변경되었습니다. 다시 시도해주세요.");
        }
        if (primaryAsset == null) {
            for (MediaAssetLink link : existingLinks) {
                mediaAssetLinkRepository.delete(link);
            }
            return new ProfileLinkResult(profileKey, null);
        }

        Optional<MediaAsset> feedAsset = mediaAssetRepository.findByDerivedFrom_Id(primaryAsset.getId());
        if (feedAsset.isPresent()) {
            MediaAsset feed = feedAsset.get();
            if (!lockedIds.contains(feed.getId()) || feed.getStatus() != MediaAssetStatus.READY
                    || feed.getDomain() != MediaDomain.PROFILE || !Objects.equals(feed.getOwnerUserId(), userId)
                    || feed.getObjectKey() == null || feed.getObjectKey().isBlank()) {
                throw new BadRequestBusinessException("MEDIA_ASSET_NOT_READY", "이미지 상태가 변경되었습니다. 다시 시도해주세요.");
            }
        }
        Set<Long> desiredAssetIds = new LinkedHashSet<>();
        desiredAssetIds.add(primaryAsset.getId());
        feedAsset.map(MediaAsset::getId).ifPresent(desiredAssetIds::add);

        for (MediaAssetLink existingLink : existingLinks) {
            if (!desiredAssetIds.contains(existingLink.getAssetId())) {
                mediaAssetLinkRepository.delete(existingLink);
            }
        }

        upsertLink(primaryAsset, existingByAssetId, MediaDomain.PROFILE, userId, MediaLinkRole.PROFILE_PRIMARY);
        String feedKey = feedAsset.map(feed -> {
                    upsertLink(feed, existingByAssetId, MediaDomain.PROFILE, userId, MediaLinkRole.PROFILE_FEED);
                    return feed.getObjectKey();
                })
                .orElse(null);

        return new ProfileLinkResult(primaryAsset.getObjectKey(), feedKey);
    }

    @Transactional
    public void syncDiaryLinks(Long diaryId, Long userId, List<String> storagePaths) {
        Set<Long> lockedIds = lockAssetsForLink(MediaDomain.DIARY, diaryId, storagePaths);
        syncLinks(MediaDomain.DIARY, diaryId, userId, storagePaths, MediaLinkRole.DIARY_PHOTO, lockedIds);
    }

    /** Keeps managed diary assets stable until derived seat-view rows are committed. */
    @Transactional
    public void lockDiaryAssetsForSeatView(Long userId, Collection<String> storagePaths) {
        List<String> keys = normalizeManagedKeys(MediaDomain.DIARY, storagePaths).stream().distinct().toList();
        if (keys.isEmpty()) return;
        entityManager.flush();
        Set<Long> lockedIds = new java.util.TreeSet<>(mediaAssetRepository.findIdsByObjectKeyInOrderById(keys));
        lockAssetIds(lockedIds);
        Map<String, MediaAsset> current = resolveReadyAssets(userId, MediaDomain.DIARY, keys);
        if (current.size() != keys.size() || current.values().stream().anyMatch(asset -> !lockedIds.contains(asset.getId()))) {
            throw new BadRequestBusinessException("MEDIA_ASSET_NOT_READY", "이미지 상태가 변경되었습니다. 다시 시도해주세요.");
        }
    }

    @Transactional
    public void linkSeatViewAsset(Long userId, String storagePath, Long seatViewId) {
        Set<Long> lockedIds = lockAssetsForLink(MediaDomain.SEAT_VIEW, seatViewId, List.of(storagePath));
        Map<String, MediaAsset> readyAssets = resolveReadyAssets(
                userId,
                MediaDomain.SEAT_VIEW,
                List.of(storagePath));
        MediaAsset asset = readyAssets.get(storagePath);
        if (asset == null || !lockedIds.contains(asset.getId())) {
            throw new BadRequestBusinessException("MEDIA_ASSET_NOT_FOUND", "업로드를 다시 완료한 뒤 저장해주세요.");
        }
        createOrValidateLink(asset, MediaDomain.SEAT_VIEW, seatViewId, MediaLinkRole.SEAT_VIEW_PHOTO);
    }

    public MediaAsset requireLinkedSeatViewAsset(Long userId, String storagePath, Long seatViewId) {
        Map<String, MediaAsset> readyAssets = resolveReadyAssets(
                userId,
                MediaDomain.SEAT_VIEW,
                List.of(storagePath));
        MediaAsset asset = readyAssets.get(storagePath);
        if (asset == null || mediaAssetLinkRepository.findById(asset.getId())
                .filter(link -> link.getDomain() == MediaDomain.SEAT_VIEW)
                .filter(link -> Objects.equals(link.getEntityId(), seatViewId))
                .filter(link -> link.getRole() == MediaLinkRole.SEAT_VIEW_PHOTO)
                .isEmpty()) {
            throw new BadRequestBusinessException("MEDIA_ASSET_NOT_FOUND", "업로드를 다시 완료한 뒤 저장해주세요.");
        }
        return asset;
    }

    @Transactional
    public void syncCheerLinks(Long postId, Long userId, List<String> storagePaths) {
        Set<Long> lockedIds = lockAssetsForLink(MediaDomain.CHEER, postId, storagePaths);
        syncLinks(MediaDomain.CHEER, postId, userId, storagePaths, MediaLinkRole.CHEER_IMAGE, lockedIds);
    }

    private Set<Long> lockAssetsForLink(MediaDomain domain, Long entityId, Collection<String> storagePaths) {
        entityManager.flush();
        Set<Long> assetIds = new java.util.TreeSet<>();
        mediaAssetLinkRepository.findByDomainAndEntityId(domain, entityId)
                .forEach(link -> assetIds.add(link.getAssetId()));
        List<String> keys = normalizeManagedKeys(domain, storagePaths);
        if (!keys.isEmpty()) assetIds.addAll(mediaAssetRepository.findIdsByObjectKeyInOrderById(keys));
        lockAssetIds(assetIds);
        return assetIds;
    }

    private void lockAssetIds(Set<Long> assetIds) {
        for (Long id : assetIds) {
            MediaAsset asset = mediaAssetRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new BadRequestBusinessException("MEDIA_ASSET_NOT_FOUND", "업로드를 다시 완료한 뒤 저장해주세요."));
            entityManager.refresh(asset, LockModeType.PESSIMISTIC_WRITE);
        }
    }

    @Transactional
    public void syncChatLink(Long messageId, Long userId, String storagePath) {
        List<String> paths = storagePath == null || storagePath.isBlank() ? List.of() : List.of(storagePath);
        Set<Long> lockedIds = lockAssetsForLink(MediaDomain.CHAT, messageId, paths);
        syncLinks(
                MediaDomain.CHAT,
                messageId,
                userId,
                paths,
                MediaLinkRole.CHAT_IMAGE,
                lockedIds);
    }

    public Map<String, MediaAsset> resolveReadyAssets(Long userId, MediaDomain domain, Collection<String> storagePaths) {
        List<String> managedKeys = normalizeManagedKeys(domain, storagePaths);
        if (managedKeys.isEmpty()) {
            return Map.of();
        }

        Map<String, MediaAsset> assetByKey = mediaAssetRepository.findByObjectKeyIn(managedKeys).stream()
                .collect(LinkedHashMap::new, (map, asset) -> map.put(asset.getObjectKey(), asset), Map::putAll);

        for (String key : managedKeys) {
            MediaAsset asset = assetByKey.get(key);
            if (asset == null) {
                throw new BadRequestBusinessException("MEDIA_ASSET_NOT_FOUND", "업로드를 다시 완료한 뒤 저장해주세요.");
            }
            if (!Objects.equals(asset.getOwnerUserId(), userId)) {
                throw new BadRequestBusinessException("MEDIA_ASSET_NOT_FOUND", "업로드를 다시 완료한 뒤 저장해주세요.");
            }
            if (domain == MediaDomain.PROFILE) {
                if (asset.getDomain() != MediaDomain.PROFILE) {
                    throw new BadRequestBusinessException("MEDIA_ASSET_DOMAIN_MISMATCH", "이미지 도메인이 요청과 일치하지 않습니다.");
                }
            } else if (asset.getDomain() != domain) {
                throw new BadRequestBusinessException("MEDIA_ASSET_DOMAIN_MISMATCH", "이미지 도메인이 요청과 일치하지 않습니다.");
            }
            if (asset.getStatus() != MediaAssetStatus.READY) {
                throw new BadRequestBusinessException("MEDIA_ASSET_NOT_READY", "업로드 완료 처리 전에는 사용할 수 없습니다.");
            }
        }

        return assetByKey;
    }

    @Transactional
    public void unlinkEntity(MediaDomain domain, Long entityId) {
        mediaAssetLinkRepository.deleteByDomainAndEntityId(domain, entityId);
    }

    public Optional<MediaAsset> findReadyAsset(String objectKey) {
        return mediaAssetRepository.findByObjectKey(objectKey)
                .filter(asset -> asset.getStatus() == MediaAssetStatus.READY);
    }

    private void syncLinks(
            MediaDomain domain,
            Long entityId,
            Long userId,
            Collection<String> storagePaths,
            MediaLinkRole role) {
        syncLinks(domain, entityId, userId, storagePaths, role, null);
    }

    private void syncLinks(
            MediaDomain domain, Long entityId, Long userId, Collection<String> storagePaths,
            MediaLinkRole role, Set<Long> lockedIds) {
        List<MediaAssetLink> existingLinks = mediaAssetLinkRepository.findByDomainAndEntityId(domain, entityId);
        if (lockedIds != null && existingLinks.stream().anyMatch(link -> !lockedIds.contains(link.getAssetId()))) {
            throw new BadRequestBusinessException("MEDIA_ASSET_NOT_READY", "이미지 상태가 변경되었습니다. 다시 시도해주세요.");
        }
        Map<Long, MediaAssetLink> existingByAssetId = new LinkedHashMap<>();
        for (MediaAssetLink link : existingLinks) {
            existingByAssetId.put(link.getAssetId(), link);
        }

        Map<String, MediaAsset> readyAssets = resolveReadyAssets(userId, domain, storagePaths);
        if (lockedIds != null && readyAssets.values().stream().anyMatch(asset -> !lockedIds.contains(asset.getId()))) {
            throw new BadRequestBusinessException("MEDIA_ASSET_NOT_FOUND", "업로드를 다시 완료한 뒤 저장해주세요.");
        }
        Set<Long> desiredAssetIds = new LinkedHashSet<>();
        for (MediaAsset asset : readyAssets.values()) {
            desiredAssetIds.add(asset.getId());
        }

        for (MediaAssetLink existingLink : existingLinks) {
            if (!desiredAssetIds.contains(existingLink.getAssetId())) {
                mediaAssetLinkRepository.delete(existingLink);
            }
        }

        for (MediaAsset asset : readyAssets.values()) {
            MediaAssetLink currentLink = existingByAssetId.get(asset.getId());
            if (currentLink != null) {
                if (currentLink.getRole() != role) {
                    currentLink.setRole(role);
                    mediaAssetLinkRepository.save(currentLink);
                }
                continue;
            }
            createOrValidateLink(asset, domain, entityId, role);
        }
    }

    private void upsertLink(
            MediaAsset asset,
            Map<Long, MediaAssetLink> existingByAssetId,
            MediaDomain domain,
            Long entityId,
            MediaLinkRole role) {
        MediaAssetLink existingLink = existingByAssetId.get(asset.getId());
        if (existingLink != null) {
            if (existingLink.getRole() != role) {
                existingLink.setRole(role);
                mediaAssetLinkRepository.save(existingLink);
            }
            return;
        }

        createOrValidateLink(asset, domain, entityId, role);
    }

    private void createOrValidateLink(MediaAsset asset, MediaDomain domain, Long entityId, MediaLinkRole role) {
        Optional<MediaAssetLink> existingLink = mediaAssetLinkRepository.findById(asset.getId());
        if (existingLink.isPresent()) {
            MediaAssetLink link = existingLink.get();
            if (!Objects.equals(link.getDomain(), domain) || !Objects.equals(link.getEntityId(), entityId)) {
                throw new BadRequestBusinessException("MEDIA_ASSET_ALREADY_LINKED", "이미 다른 데이터에 연결된 이미지는 재사용할 수 없습니다.");
            }
            if (link.getRole() != role) {
                link.setRole(role);
                mediaAssetLinkRepository.save(link);
            }
            return;
        }

        mediaAssetLinkRepository.save(MediaAssetLink.builder()
                .assetId(asset.getId())
                .asset(asset)
                .domain(domain)
                .entityId(entityId)
                .role(role)
                .build());
    }

    private List<String> normalizeManagedKeys(MediaDomain domain, Collection<String> storagePaths) {
        if (storagePaths == null || storagePaths.isEmpty()) {
            return List.of();
        }

        List<String> managedKeys = new ArrayList<>();
        for (String storagePath : storagePaths) {
            if (storagePath == null || storagePath.isBlank()) {
                continue;
            }
            String normalized = storagePath.strip();
            if (domain.isManagedPath(normalized)) {
                managedKeys.add(normalized);
            }
        }
        return managedKeys;
    }

    public record ProfileLinkResult(
            String profileObjectKey,
            String profileFeedObjectKey) {
    }
}
