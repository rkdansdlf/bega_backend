package com.example.media.service;

import com.example.common.exception.BadRequestBusinessException;
import com.example.media.entity.MediaAsset;
import com.example.media.entity.MediaAssetLink;
import com.example.media.entity.MediaAssetStatus;
import com.example.media.entity.MediaDomain;
import com.example.media.entity.MediaLinkRole;
import com.example.media.repository.MediaAssetLinkRepository;
import com.example.media.repository.MediaAssetRepository;
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

    @Transactional
    public ProfileLinkResult syncProfileLinks(Long userId, String profileKey) {
        List<MediaAssetLink> existingLinks = mediaAssetLinkRepository.findByDomainAndEntityId(MediaDomain.PROFILE, userId);
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
        if (primaryAsset == null) {
            for (MediaAssetLink link : existingLinks) {
                mediaAssetLinkRepository.delete(link);
            }
            return new ProfileLinkResult(profileKey, null);
        }

        Optional<MediaAsset> feedAsset = mediaAssetRepository.findByDerivedFrom_Id(primaryAsset.getId());
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
        syncLinks(MediaDomain.DIARY, diaryId, userId, storagePaths, MediaLinkRole.DIARY_PHOTO);
    }

    @Transactional
    public void syncCheerLinks(Long postId, Long userId, List<String> storagePaths) {
        syncLinks(MediaDomain.CHEER, postId, userId, storagePaths, MediaLinkRole.CHEER_IMAGE);
    }

    @Transactional
    public void syncChatLink(Long messageId, Long userId, String storagePath) {
        syncLinks(
                MediaDomain.CHAT,
                messageId,
                userId,
                storagePath == null || storagePath.isBlank() ? List.of() : List.of(storagePath),
                MediaLinkRole.CHAT_IMAGE);
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
                throw new BadRequestBusinessException("MEDIA_ASSET_OWNER_MISMATCH", "다른 사용자의 이미지에는 접근할 수 없습니다.");
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
        List<MediaAssetLink> existingLinks = mediaAssetLinkRepository.findByDomainAndEntityId(domain, entityId);
        Map<Long, MediaAssetLink> existingByAssetId = new LinkedHashMap<>();
        for (MediaAssetLink link : existingLinks) {
            existingByAssetId.put(link.getAssetId(), link);
        }

        Map<String, MediaAsset> readyAssets = resolveReadyAssets(userId, domain, storagePaths);
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
