package com.example.media.service;

import com.example.BegaDiary.Entity.BegaDiary;
import com.example.BegaDiary.Repository.BegaDiaryRepository;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.service.ImageService;
import com.example.cheerboard.storage.strategy.StorageStrategy;
import com.example.cheerboard.storage.strategy.StoredObject;
import com.example.common.image.ImageUtil;
import com.example.mate.entity.ChatMessage;
import com.example.mate.repository.ChatMessageRepository;
import com.example.mate.service.ChatImageService;
import com.example.media.dto.MediaBackfillDomainReport;
import com.example.media.dto.MediaBackfillReport;
import com.example.media.dto.MediaCleanupReport;
import com.example.media.dto.MediaCleanupTargetReport;
import com.example.media.dto.MediaSmokeDomainReport;
import com.example.media.dto.MediaSmokeReport;
import com.example.media.entity.MediaAsset;
import com.example.media.entity.MediaAssetStatus;
import com.example.media.entity.MediaCleanupTarget;
import com.example.media.entity.MediaDomain;
import com.example.media.support.ByteArrayMultipartFile;
import com.example.media.repository.MediaAssetRepository;
import com.example.profile.storage.service.ProfileImageService;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaMaintenanceService {

    private static final int MAX_SAMPLE_LIMIT = 50;
    private static final int MAX_BATCH_SIZE = 1000;
    private static final int MAX_AFFECTED_SAMPLES = 10;

    private final MediaAssetRepository mediaAssetRepository;
    private final UserRepository userRepository;
    private final BegaDiaryRepository begaDiaryRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final StorageStrategy storageStrategy;
    private final StorageConfig storageConfig;
    private final ProfileImageService profileImageService;
    private final ImageService imageService;
    private final ChatImageService chatImageService;
    private final MediaLinkService mediaLinkService;
    private final MediaUploadService mediaUploadService;
    private final ImageUtil imageUtil;

    public MediaSmokeReport runSmoke(int sampleLimit, Collection<MediaDomain> requestedDomains) {
        int normalizedSampleLimit = clamp(sampleLimit, 1, MAX_SAMPLE_LIMIT);
        List<MediaDomain> domainsToRun = resolveDomains(requestedDomains);
        List<MediaSmokeDomainReport> domains = domainsToRun.stream()
                .map(domain -> smokeDomain(domain, normalizedSampleLimit))
                .toList();
        boolean hasFailures = domains.stream().anyMatch(this::hasSmokeFailures);
        return new MediaSmokeReport(
                normalizedSampleLimit,
                domainsToRun.stream().map(Enum::name).toList(),
                domains,
                hasFailures);
    }

    public MediaBackfillReport backfillExistingData(
            boolean apply,
            int batchSize,
            Collection<MediaDomain> requestedDomains,
            boolean clearBrokenChatImages) {
        int normalizedBatchSize = clamp(batchSize, 1, MAX_BATCH_SIZE);
        List<MediaDomain> domainsToRun = resolveDomains(requestedDomains);
        List<MediaBackfillDomainReport> domains = domainsToRun.stream()
                .map(domain -> switch (domain) {
                    case PROFILE -> backfillProfiles(apply, normalizedBatchSize);
                    case DIARY -> backfillDiaries(apply, normalizedBatchSize);
                    case CHAT -> backfillChats(apply, normalizedBatchSize, clearBrokenChatImages);
                    case CHEER -> new MediaBackfillDomainReport(
                            "CHEER",
                            0,
                            0,
                            0,
                            0,
                            0,
                            0,
                            0,
                            List.of(),
                            List.of(),
                            List.of());
                })
                .toList();
        boolean hasFailures = domains.stream().anyMatch(report -> report.manualReviewCount() > 0);
        return new MediaBackfillReport(
                apply,
                normalizedBatchSize,
                domainsToRun.stream().map(Enum::name).toList(),
                domains,
                hasFailures);
    }

    public MediaCleanupReport runCleanup(Collection<MediaCleanupTarget> requestedTargets) {
        List<MediaCleanupTarget> targetsToRun = resolveCleanupTargets(requestedTargets);
        List<MediaCleanupTargetReport> targets = targetsToRun.stream()
                .map(target -> switch (target) {
                    case PENDING -> mediaUploadService.cleanupExpiredPendingAssets();
                    case ORPHAN -> mediaUploadService.cleanupUnlinkedReadyAssets();
                })
                .toList();
        boolean hasFailures = targets.stream().anyMatch(report -> report.errorCount() > 0);
        return new MediaCleanupReport(
                targetsToRun.stream().map(Enum::name).toList(),
                targets,
                hasFailures);
    }

    private MediaSmokeDomainReport smokeDomain(MediaDomain domain, int sampleLimit) {
        Pageable pageable = PageRequest.of(0, sampleLimit, Sort.by(Sort.Direction.DESC, "id"));
        List<MediaAsset> assets = domain == MediaDomain.PROFILE
                ? mediaAssetRepository.findByDomainAndStatusAndDerivedFromIsNullOrderByIdDesc(
                        domain,
                        MediaAssetStatus.READY,
                        pageable)
                : mediaAssetRepository.findByDomainAndStatusOrderByIdDesc(domain, MediaAssetStatus.READY, pageable);

        int missingObjectCount = 0;
        int urlFailureCount = 0;
        int feedDerivativeMissingCount = 0;
        List<String> failedKeys = new ArrayList<>();

        for (MediaAsset asset : assets) {
            SmokeCheck primaryCheck = checkAsset(asset);
            if (primaryCheck.missingObject()) {
                missingObjectCount++;
                failedKeys.add(asset.getObjectKey());
            } else if (primaryCheck.urlFailure()) {
                urlFailureCount++;
                failedKeys.add(asset.getObjectKey());
            }

            if (domain == MediaDomain.PROFILE) {
                Optional<MediaAsset> feedAsset = mediaAssetRepository.findByDerivedFrom_Id(asset.getId());
                if (feedAsset.isEmpty()) {
                    feedDerivativeMissingCount++;
                    failedKeys.add(asset.getObjectKey() + " (feed_missing)");
                    continue;
                }

                SmokeCheck feedCheck = checkAsset(feedAsset.get());
                if (feedCheck.missingObject()) {
                    missingObjectCount++;
                    failedKeys.add(feedAsset.get().getObjectKey());
                } else if (feedCheck.urlFailure()) {
                    urlFailureCount++;
                    failedKeys.add(feedAsset.get().getObjectKey());
                }
            }
        }

        return new MediaSmokeDomainReport(
                domain,
                assets.size(),
                missingObjectCount,
                urlFailureCount,
                feedDerivativeMissingCount,
                failedKeys);
    }

    private MediaBackfillDomainReport backfillProfiles(boolean apply, int batchSize) {
        int scannedCount = 0;
        int normalizedCount = 0;
        int updatedCount = 0;
        int clearedCount = 0;
        int linkSyncedCount = 0;
        int legacyPathRetainedCount = 0;
        int manualReviewCount = 0;
        List<String> sampleNormalizedTargets = new ArrayList<>();
        List<String> sampleLegacyTargets = new ArrayList<>();
        List<String> sampleManualReviewTargets = new ArrayList<>();

        Pageable pageable = PageRequest.of(0, batchSize, Sort.by(Sort.Direction.ASC, "id"));
        while (true) {
            Page<UserEntity> page = userRepository.findAll(pageable);
            for (UserEntity user : page.getContent()) {
                scannedCount++;

                String rawProfile = normalizeBlank(user.getProfileImageUrl());
                String rawFeed = normalizeBlank(user.getProfileFeedImageUrl());
                String finalProfile = normalizeBlank(profileImageService.normalizeProfileStoragePath(rawProfile));
                String finalFeed = normalizeBlank(profileImageService.normalizeProfileStoragePath(rawFeed));
                boolean profileReadyForLink = false;
                boolean skipLinkSync = false;

                try {
                    ProfilePromotionResult promoted = promoteProfilePaths(user.getId(), finalProfile, finalFeed, apply);
                    finalProfile = promoted.profileKey();
                    finalFeed = promoted.feedKey();

                    if (StringUtils.hasText(finalProfile) && MediaDomain.PROFILE.isManagedPath(finalProfile)) {
                        Map<String, MediaAsset> readyAssets =
                                mediaLinkService.resolveReadyAssets(user.getId(), MediaDomain.PROFILE, List.of(finalProfile));
                        MediaAsset primaryAsset = readyAssets.get(finalProfile);
                        if (primaryAsset != null) {
                            finalProfile = primaryAsset.getObjectKey();
                            finalFeed = mediaAssetRepository.findByDerivedFrom_Id(primaryAsset.getId())
                                    .map(MediaAsset::getObjectKey)
                                    .orElse(finalFeed);
                            linkSyncedCount++;
                            profileReadyForLink = true;
                        }
                    }
                } catch (RuntimeException ex) {
                    manualReviewCount++;
                    skipLinkSync = true;
                    addSample(sampleManualReviewTargets, "userId=" + user.getId() + ":" + ex.getMessage());
                    log.warn("Profile media backfill skipped: userId={}, reason={}", user.getId(), ex.getMessage());
                }

                if (StringUtils.hasText(finalProfile) && !MediaDomain.PROFILE.isManagedPath(finalProfile)) {
                    legacyPathRetainedCount++;
                    addSample(sampleLegacyTargets, "userId=" + user.getId() + ":" + finalProfile);
                }

                if (!StringUtils.hasText(finalProfile) && StringUtils.hasText(finalFeed)) {
                    manualReviewCount++;
                }

                boolean changed = !Objects.equals(rawProfile, finalProfile) || !Objects.equals(rawFeed, finalFeed);
                if (changed) {
                    normalizedCount++;
                    addSample(sampleNormalizedTargets, "userId=" + user.getId() + ":" + safeJoin(finalProfile, finalFeed));
                }

                if (apply && changed) {
                    userRepository.updateProfileImageUrlsById(user.getId(), finalProfile, finalFeed);
                    updatedCount++;
                }

                if (apply) {
                    if (!skipLinkSync) {
                        String syncKey = profileReadyForLink ? finalProfile : null;
                        mediaLinkService.syncProfileLinks(user.getId(), syncKey);
                    }
                }
            }

            if (!page.hasNext()) {
                break;
            }
            pageable = page.nextPageable();
        }

        return new MediaBackfillDomainReport(
                "PROFILE",
                scannedCount,
                normalizedCount,
                updatedCount,
                clearedCount,
                linkSyncedCount,
                legacyPathRetainedCount,
                manualReviewCount,
                sampleNormalizedTargets,
                sampleLegacyTargets,
                sampleManualReviewTargets);
    }

    private MediaBackfillDomainReport backfillDiaries(boolean apply, int batchSize) {
        int scannedCount = 0;
        int normalizedCount = 0;
        int updatedCount = 0;
        int clearedCount = 0;
        int linkSyncedCount = 0;
        int legacyPathRetainedCount = 0;
        int manualReviewCount = 0;
        List<String> sampleNormalizedTargets = new ArrayList<>();
        List<String> sampleLegacyTargets = new ArrayList<>();
        List<String> sampleManualReviewTargets = new ArrayList<>();

        Pageable pageable = PageRequest.of(0, batchSize, Sort.by(Sort.Direction.ASC, "id"));
        while (true) {
            Page<BegaDiary> page = begaDiaryRepository.findAllBy(pageable);
            for (BegaDiary diary : page.getContent()) {
                scannedCount++;

                List<String> rawPaths = diary.getPhotoUrls() == null ? List.of() : List.copyOf(diary.getPhotoUrls());
                List<String> finalPaths = imageService.normalizeDiaryStoragePaths(rawPaths);
                boolean diaryReadyForLink = false;
                boolean skipLinkSync = false;

                try {
                    if (apply) {
                        finalPaths = finalPaths.stream()
                                .map(path -> promoteManagedStoragePath(diary.getUser().getId(), MediaDomain.DIARY, path))
                                .toList();
                    }

                    List<String> managedKeys = managedKeys(finalPaths, MediaDomain.DIARY);
                    if (!managedKeys.isEmpty()) {
                        mediaLinkService.resolveReadyAssets(diary.getUser().getId(), MediaDomain.DIARY, finalPaths);
                        linkSyncedCount++;
                        diaryReadyForLink = true;
                    }
                } catch (RuntimeException ex) {
                    manualReviewCount++;
                    skipLinkSync = true;
                    addSample(sampleManualReviewTargets, "diaryId=" + diary.getId() + ":" + ex.getMessage());
                    log.warn("Diary media backfill skipped: diaryId={}, reason={}", diary.getId(), ex.getMessage());
                }

                legacyPathRetainedCount += countLegacyPaths(finalPaths, MediaDomain.DIARY);
                collectLegacySamples(sampleLegacyTargets, "diaryId=" + diary.getId(), finalPaths, MediaDomain.DIARY);

                if (!Objects.equals(rawPaths, finalPaths)) {
                    normalizedCount++;
                    addSample(sampleNormalizedTargets, "diaryId=" + diary.getId() + ":" + String.join(",", finalPaths));
                    if (apply) {
                        diary.getPhotoUrls().clear();
                        diary.getPhotoUrls().addAll(finalPaths);
                        begaDiaryRepository.save(diary);
                        updatedCount++;
                    }
                }

                if (apply) {
                    if (diaryReadyForLink) {
                        mediaLinkService.syncDiaryLinks(diary.getId(), diary.getUser().getId(), finalPaths);
                    } else if (!skipLinkSync) {
                        mediaLinkService.unlinkEntity(MediaDomain.DIARY, diary.getId());
                    }
                }
            }

            if (!page.hasNext()) {
                break;
            }
            pageable = page.nextPageable();
        }

        return new MediaBackfillDomainReport(
                "DIARY",
                scannedCount,
                normalizedCount,
                updatedCount,
                clearedCount,
                linkSyncedCount,
                legacyPathRetainedCount,
                manualReviewCount,
                sampleNormalizedTargets,
                sampleLegacyTargets,
                sampleManualReviewTargets);
    }

    private MediaBackfillDomainReport backfillChats(boolean apply, int batchSize, boolean clearBrokenChatImages) {
        int scannedCount = 0;
        int normalizedCount = 0;
        int updatedCount = 0;
        int clearedCount = 0;
        int linkSyncedCount = 0;
        int legacyPathRetainedCount = 0;
        int manualReviewCount = 0;
        List<String> sampleNormalizedTargets = new ArrayList<>();
        List<String> sampleLegacyTargets = new ArrayList<>();
        List<String> sampleManualReviewTargets = new ArrayList<>();

        Pageable pageable = PageRequest.of(0, batchSize, Sort.by(Sort.Direction.ASC, "id"));
        while (true) {
            Page<ChatMessage> page = chatMessageRepository.findAll(pageable);
            for (ChatMessage message : page.getContent()) {
                scannedCount++;

                String rawImage = normalizeBlank(message.getImageUrl());
                String finalImage = normalizeBlank(chatImageService.normalizeChatStoragePath(rawImage));
                boolean chatReadyForLink = false;
                boolean skipLinkSync = false;
                boolean clearedBrokenReference = false;

                try {
                    if (apply) {
                        finalImage = normalizeBlank(promoteManagedStoragePath(message.getSenderId(), MediaDomain.CHAT, finalImage));
                    }

                    if (StringUtils.hasText(finalImage) && MediaDomain.CHAT.isManagedPath(finalImage)) {
                        mediaLinkService.resolveReadyAssets(message.getSenderId(), MediaDomain.CHAT, List.of(finalImage));
                        linkSyncedCount++;
                        chatReadyForLink = true;
                    }
                } catch (RuntimeException ex) {
                    skipLinkSync = true;
                    String reviewMessage = "messageId=" + message.getId() + ":" + ex.getMessage();
                    if (shouldClearBrokenLegacyChatImage(apply, clearBrokenChatImages, finalImage, ex)) {
                        finalImage = null;
                        normalizedCount++;
                        updatedCount++;
                        clearedCount++;
                        clearedBrokenReference = true;
                        addSample(sampleNormalizedTargets, "messageId=" + message.getId() + ":(cleared broken legacy chat image)");
                        message.setImageUrl(null);
                        chatMessageRepository.save(message);
                        mediaLinkService.unlinkEntity(MediaDomain.CHAT, message.getId());
                    } else {
                        manualReviewCount++;
                        addSample(sampleManualReviewTargets, reviewMessage);
                    }
                    log.warn("Chat media backfill skipped: messageId={}, reason={}", message.getId(), ex.getMessage());
                }

                if (StringUtils.hasText(finalImage) && !MediaDomain.CHAT.isManagedPath(finalImage)) {
                    legacyPathRetainedCount++;
                    addSample(sampleLegacyTargets, "messageId=" + message.getId() + ":" + finalImage);
                }

                if (!clearedBrokenReference && !Objects.equals(rawImage, finalImage)) {
                    normalizedCount++;
                    addSample(sampleNormalizedTargets, "messageId=" + message.getId() + ":" + finalImage);
                    if (apply) {
                        message.setImageUrl(finalImage);
                        chatMessageRepository.save(message);
                        updatedCount++;
                    }
                }

                if (apply) {
                    if (chatReadyForLink) {
                        mediaLinkService.syncChatLink(message.getId(), message.getSenderId(), finalImage);
                    } else if (!skipLinkSync) {
                        mediaLinkService.unlinkEntity(MediaDomain.CHAT, message.getId());
                    }
                }
            }

            if (!page.hasNext()) {
                break;
            }
            pageable = page.nextPageable();
        }

        return new MediaBackfillDomainReport(
                "CHAT",
                scannedCount,
                normalizedCount,
                updatedCount,
                clearedCount,
                linkSyncedCount,
                legacyPathRetainedCount,
                manualReviewCount,
                sampleNormalizedTargets,
                sampleLegacyTargets,
                sampleManualReviewTargets);
    }

    private SmokeCheck checkAsset(MediaAsset asset) {
        String bucket = asset.getDomain().resolveBucket(storageConfig);
        try {
            boolean exists = Boolean.TRUE.equals(storageStrategy.exists(bucket, asset.getObjectKey()).block());
            if (!exists) {
                return new SmokeCheck(true, false);
            }

            String url = storageStrategy
                    .getUrl(bucket, asset.getObjectKey(), storageConfig.getSignedUrlTtlSeconds())
                    .block();
            return new SmokeCheck(false, !StringUtils.hasText(url));
        } catch (RuntimeException ex) {
            log.warn("Media smoke check failed: assetId={}, key={}, reason={}",
                    asset.getId(), asset.getObjectKey(), ex.getMessage());
            return new SmokeCheck(false, true);
        }
    }

    private ProfilePromotionResult promoteProfilePaths(Long userId, String profilePath, String feedPath, boolean apply) {
        if (!StringUtils.hasText(profilePath) || !apply) {
            return new ProfilePromotionResult(profilePath, feedPath);
        }

        String normalizedProfile = profilePath.strip();
        if (MediaDomain.PROFILE.isManagedPath(normalizedProfile)) {
            MediaAsset primaryAsset = ensureManagedAsset(userId, MediaDomain.PROFILE, normalizedProfile);
            return new ProfilePromotionResult(primaryAsset.getObjectKey(), ensureProfileFeedAsset(primaryAsset));
        }

        if (isLegacyInternalPath(MediaDomain.PROFILE, normalizedProfile)) {
            return migrateLegacyProfileAsset(userId, normalizedProfile);
        }

        return new ProfilePromotionResult(normalizedProfile, feedPath);
    }

    private String promoteManagedStoragePath(Long userId, MediaDomain domain, String storagePath) {
        if (!StringUtils.hasText(storagePath)) {
            return storagePath;
        }

        String normalizedPath = storagePath.strip();
        if (domain.isManagedPath(normalizedPath)) {
            return ensureManagedAsset(userId, domain, normalizedPath).getObjectKey();
        }

        if (isLegacyInternalPath(domain, normalizedPath)) {
            return migrateLegacyStoragePath(userId, domain, normalizedPath);
        }

        return normalizedPath;
    }

    private MediaAsset ensureManagedAsset(Long userId, MediaDomain domain, String storagePath) {
        Optional<MediaAsset> existing = mediaAssetRepository.findByObjectKey(storagePath);
        if (existing.isPresent()) {
            MediaAsset asset = existing.get();
            validateManagedAsset(asset, userId, domain);
            return asset;
        }

        return registerExistingManagedAsset(userId, domain, storagePath);
    }

    private MediaAsset registerExistingManagedAsset(Long userId, MediaDomain domain, String storagePath) {
        StoredObject storedObject = downloadStoredObject(domain, storagePath);
        ImageUtil.ImageDimension dimension = imageUtil.getImageDimension(storedObject.bytes());
        String contentType = resolveContentType(storedObject, storagePath);
        MediaAsset asset = createBackfillAsset(
                userId,
                domain,
                storagePath,
                extractFileName(storagePath),
                contentType,
                storedObject.size(),
                dimension,
                null);
        asset.markReady(storagePath, contentType, storedObject.size(), dimension.width(), dimension.height());
        return mediaAssetRepository.save(asset);
    }

    private ProfilePromotionResult migrateLegacyProfileAsset(Long userId, String storagePath) {
        StoredObject storedObject = downloadStoredObject(MediaDomain.PROFILE, storagePath);
        ImageUtil.ImageDimension sourceDimension = imageUtil.getImageDimension(storedObject.bytes());
        String originalFileName = extractFileName(storagePath);
        String sourceContentType = resolveContentType(storedObject, originalFileName);
        ByteArrayMultipartFile file = new ByteArrayMultipartFile(originalFileName, sourceContentType, storedObject.bytes());

        MediaAsset primaryAsset = createBackfillAsset(
                userId,
                MediaDomain.PROFILE,
                storagePath,
                originalFileName,
                sourceContentType,
                storedObject.size(),
                sourceDimension,
                null);
        MediaAsset feedAsset = null;
        String primaryKey = null;
        String feedKey = null;
        try {
            ImageUtil.ProcessedImage processedImage = imageUtil.processProfileImage(file, "media_backfill_profile");
            ImageUtil.ImageDimension storedDimension = imageUtil.getImageDimension(processedImage.getBytes());
            primaryKey = MediaDomain.PROFILE.buildFinalObjectKey(userId, primaryAsset.getId(), processedImage.getExtension());
            storageStrategy.uploadBytes(
                    processedImage.getBytes(),
                    processedImage.getContentType(),
                    storageConfig.getProfileBucket(),
                    primaryKey).block();
            primaryAsset.markReady(
                    primaryKey,
                    processedImage.getContentType(),
                    processedImage.getSize(),
                    storedDimension.width(),
                    storedDimension.height());
            mediaAssetRepository.save(primaryAsset);

            ImageUtil.ProcessedImage feedImage = imageUtil.processFeedProfileImage(file, "media_backfill_profile_feed");
            ImageUtil.ImageDimension feedDimension = imageUtil.getImageDimension(feedImage.getBytes());
            feedKey = MediaDomain.PROFILE.buildProfileFeedObjectKey(userId, primaryAsset.getId(), feedImage.getExtension());
            storageStrategy.uploadBytes(
                    feedImage.getBytes(),
                    feedImage.getContentType(),
                    storageConfig.getProfileBucket(),
                    feedKey).block();

            feedAsset = createBackfillAsset(
                    userId,
                    MediaDomain.PROFILE,
                    storagePath,
                    originalFileName,
                    sourceContentType,
                    storedObject.size(),
                    sourceDimension,
                    primaryAsset);
            feedAsset.markReady(
                    feedKey,
                    feedImage.getContentType(),
                    feedImage.getSize(),
                    feedDimension.width(),
                    feedDimension.height());
            mediaAssetRepository.save(feedAsset);
            return new ProfilePromotionResult(primaryKey, feedKey);
        } catch (RuntimeException ex) {
            rollbackBackfillAsset(primaryAsset, MediaDomain.PROFILE, primaryKey);
            rollbackBackfillAsset(feedAsset, MediaDomain.PROFILE, feedKey);
            throw ex;
        } catch (IOException ex) {
            rollbackBackfillAsset(primaryAsset, MediaDomain.PROFILE, primaryKey);
            rollbackBackfillAsset(feedAsset, MediaDomain.PROFILE, feedKey);
            throw new IllegalStateException("기존 프로필 이미지 승격 중 오류가 발생했습니다.", ex);
        }
    }

    private String migrateLegacyStoragePath(Long userId, MediaDomain domain, String storagePath) {
        StoredObject storedObject = downloadStoredObject(domain, storagePath);
        ImageUtil.ImageDimension sourceDimension = imageUtil.getImageDimension(storedObject.bytes());
        String originalFileName = extractFileName(storagePath);
        String sourceContentType = resolveContentType(storedObject, originalFileName);
        ByteArrayMultipartFile file = new ByteArrayMultipartFile(originalFileName, sourceContentType, storedObject.bytes());

        MediaAsset asset = createBackfillAsset(
                userId,
                domain,
                storagePath,
                originalFileName,
                sourceContentType,
                storedObject.size(),
                sourceDimension,
                null);
        String finalObjectKey = null;
        try {
            ImageUtil.ProcessedImage processedImage = imageUtil.process(file, "media_backfill_" + domain.getPathSegment());
            ImageUtil.ImageDimension storedDimension = imageUtil.getImageDimension(processedImage.getBytes());
            finalObjectKey = domain.buildFinalObjectKey(userId, asset.getId(), processedImage.getExtension());
            storageStrategy.uploadBytes(
                    processedImage.getBytes(),
                    processedImage.getContentType(),
                    domain.resolveBucket(storageConfig),
                    finalObjectKey).block();
            asset.markReady(
                    finalObjectKey,
                    processedImage.getContentType(),
                    processedImage.getSize(),
                    storedDimension.width(),
                    storedDimension.height());
            mediaAssetRepository.save(asset);
            return finalObjectKey;
        } catch (RuntimeException ex) {
            rollbackBackfillAsset(asset, domain, finalObjectKey);
            throw ex;
        } catch (IOException ex) {
            rollbackBackfillAsset(asset, domain, finalObjectKey);
            throw new IllegalStateException("기존 이미지 승격 중 오류가 발생했습니다.", ex);
        }
    }

    private String ensureProfileFeedAsset(MediaAsset primaryAsset) {
        Optional<MediaAsset> existingFeedAsset = mediaAssetRepository.findByDerivedFrom_Id(primaryAsset.getId());
        if (existingFeedAsset.isPresent()) {
            MediaAsset asset = existingFeedAsset.get();
            validateManagedAsset(asset, primaryAsset.getOwnerUserId(), MediaDomain.PROFILE);
            return asset.getObjectKey();
        }

        StoredObject storedObject = downloadStoredObject(MediaDomain.PROFILE, primaryAsset.getObjectKey());
        String originalFileName = StringUtils.hasText(primaryAsset.getOriginalFileName())
                ? primaryAsset.getOriginalFileName()
                : extractFileName(primaryAsset.getObjectKey());
        String contentType = resolveContentType(storedObject, originalFileName);
        ByteArrayMultipartFile file = new ByteArrayMultipartFile(originalFileName, contentType, storedObject.bytes());

        MediaAsset feedAsset = createBackfillAsset(
                primaryAsset.getOwnerUserId(),
                MediaDomain.PROFILE,
                primaryAsset.getObjectKey(),
                originalFileName,
                contentType,
                storedObject.size(),
                imageUtil.getImageDimension(storedObject.bytes()),
                primaryAsset);
        String feedKey = null;
        try {
            ImageUtil.ProcessedImage feedImage = imageUtil.processFeedProfileImage(file, "media_backfill_profile_feed");
            ImageUtil.ImageDimension feedDimension = imageUtil.getImageDimension(feedImage.getBytes());
            feedKey = MediaDomain.PROFILE.buildProfileFeedObjectKey(
                    primaryAsset.getOwnerUserId(),
                    primaryAsset.getId(),
                    feedImage.getExtension());
            storageStrategy.uploadBytes(
                    feedImage.getBytes(),
                    feedImage.getContentType(),
                    storageConfig.getProfileBucket(),
                    feedKey).block();
            feedAsset.markReady(
                    feedKey,
                    feedImage.getContentType(),
                    feedImage.getSize(),
                    feedDimension.width(),
                    feedDimension.height());
            mediaAssetRepository.save(feedAsset);
            return feedKey;
        } catch (RuntimeException ex) {
            rollbackBackfillAsset(feedAsset, MediaDomain.PROFILE, feedKey);
            throw ex;
        } catch (IOException ex) {
            rollbackBackfillAsset(feedAsset, MediaDomain.PROFILE, feedKey);
            throw new IllegalStateException("프로필 feed derivative 복원 중 오류가 발생했습니다.", ex);
        }
    }

    private StoredObject downloadStoredObject(MediaDomain domain, String storagePath) {
        StoredObject storedObject = storageStrategy
                .download(domain.resolveBucket(storageConfig), storagePath)
                .block();
        if (storedObject == null || storedObject.bytes() == null || storedObject.bytes().length == 0) {
            throw new IllegalStateException("스토리지 객체를 읽을 수 없습니다: " + storagePath);
        }
        return storedObject;
    }

    private MediaAsset createBackfillAsset(
            Long userId,
            MediaDomain domain,
            String sourcePath,
            String originalFileName,
            String contentType,
            long size,
            ImageUtil.ImageDimension dimension,
            MediaAsset derivedFrom) {
        return mediaAssetRepository.saveAndFlush(MediaAsset.builder()
                .ownerUserId(userId)
                .domain(domain)
                .status(MediaAssetStatus.PENDING)
                .originalFileName(originalFileName)
                .declaredContentType(contentType)
                .declaredBytes(size)
                .declaredWidth(dimension.width())
                .declaredHeight(dimension.height())
                .stagingObjectKey(sourcePath)
                .uploadExpiresAt(LocalDateTime.now())
                .derivedFrom(derivedFrom)
                .build());
    }

    private void validateManagedAsset(MediaAsset asset, Long userId, MediaDomain domain) {
        if (!Objects.equals(asset.getOwnerUserId(), userId)) {
            throw new IllegalStateException("기존 미디어의 소유자가 현재 사용자와 일치하지 않습니다.");
        }
        if (asset.getDomain() != domain) {
            throw new IllegalStateException("기존 미디어의 도메인이 예상과 일치하지 않습니다.");
        }
        if (asset.getStatus() != MediaAssetStatus.READY) {
            throw new IllegalStateException("기존 미디어가 READY 상태가 아닙니다.");
        }
    }

    private void rollbackBackfillAsset(MediaAsset asset, MediaDomain domain, String objectKey) {
        if (asset == null) {
            return;
        }
        deleteQuietly(domain, objectKey);
        asset.markDeleted();
        mediaAssetRepository.save(asset);
    }

    private void deleteQuietly(MediaDomain domain, String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return;
        }
        try {
            storageStrategy.delete(domain.resolveBucket(storageConfig), objectKey).block();
        } catch (RuntimeException ex) {
            log.warn("Backfill cleanup failed: domain={}, key={}, reason={}", domain, objectKey, ex.getMessage());
        }
    }

    private boolean isLegacyInternalPath(MediaDomain domain, String storagePath) {
        if (!StringUtils.hasText(storagePath)) {
            return false;
        }

        return switch (domain) {
            case PROFILE -> storagePath.startsWith("profiles/");
            case DIARY -> storagePath.startsWith("diary/");
            case CHAT -> storagePath.startsWith("chat/");
            case CHEER -> false;
        };
    }

    private boolean shouldClearBrokenLegacyChatImage(
            boolean apply,
            boolean clearBrokenChatImages,
            String storagePath,
            RuntimeException exception) {
        if (!apply || !clearBrokenChatImages || !isLegacyInternalPath(MediaDomain.CHAT, storagePath)) {
            return false;
        }
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            return false;
        }
        return message.contains("이미지 치수 확인 실패")
                || message.contains("이미지 데이터를 읽을 수 없습니다")
                || message.contains("스토리지 객체를 읽을 수 없습니다");
    }

    private String resolveContentType(StoredObject storedObject, String fileNameOrPath) {
        if (storedObject != null && StringUtils.hasText(storedObject.contentType())) {
            return storedObject.contentType();
        }

        String extension = extractExtension(fileNameOrPath);
        return switch (extension) {
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "jpg", "jpeg" -> "image/jpeg";
            default -> "application/octet-stream";
        };
    }

    private String extractFileName(String storagePath) {
        if (!StringUtils.hasText(storagePath)) {
            return "legacy-upload.bin";
        }
        int separator = storagePath.lastIndexOf('/');
        return separator == -1 ? storagePath : storagePath.substring(separator + 1);
    }

    private String extractExtension(String fileNameOrPath) {
        if (!StringUtils.hasText(fileNameOrPath)) {
            return "";
        }
        String fileName = extractFileName(fileNameOrPath);
        int separator = fileName.lastIndexOf('.');
        if (separator == -1 || separator == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(separator + 1).toLowerCase(java.util.Locale.ROOT);
    }

    private List<String> managedKeys(Collection<String> values, MediaDomain domain) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        List<String> managedKeys = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.hasText(value) && domain.isManagedPath(value)) {
                managedKeys.add(value);
            }
        }
        return managedKeys;
    }

    private int countLegacyPaths(Collection<String> values, MediaDomain domain) {
        if (values == null || values.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (String value : values) {
            if (StringUtils.hasText(value) && !domain.isManagedPath(value)) {
                count++;
            }
        }
        return count;
    }

    private boolean hasSmokeFailures(MediaSmokeDomainReport report) {
        return report.missingObjectCount() > 0
                || report.urlFailureCount() > 0
                || report.feedDerivativeMissingCount() > 0;
    }

    private List<MediaDomain> resolveDomains(Collection<MediaDomain> requestedDomains) {
        if (requestedDomains == null || requestedDomains.isEmpty()) {
            return List.of(MediaDomain.PROFILE, MediaDomain.DIARY, MediaDomain.CHEER, MediaDomain.CHAT);
        }

        Set<MediaDomain> deduped = java.util.EnumSet.copyOf(requestedDomains);
        return List.copyOf(deduped);
    }

    private List<MediaCleanupTarget> resolveCleanupTargets(Collection<MediaCleanupTarget> requestedTargets) {
        if (requestedTargets == null || requestedTargets.isEmpty()) {
            return List.of(MediaCleanupTarget.PENDING, MediaCleanupTarget.ORPHAN);
        }

        Set<MediaCleanupTarget> deduped = java.util.EnumSet.copyOf(requestedTargets);
        return List.copyOf(deduped);
    }

    private void addSample(List<String> target, String value) {
        if (!StringUtils.hasText(value) || target.size() >= MAX_AFFECTED_SAMPLES) {
            return;
        }
        target.add(value);
    }

    private void collectLegacySamples(
            List<String> target,
            String prefix,
            Collection<String> values,
            MediaDomain domain) {
        if (values == null || values.isEmpty()) {
            return;
        }
        for (String value : values) {
            if (StringUtils.hasText(value) && !domain.isManagedPath(value)) {
                addSample(target, prefix + ":" + value);
            }
        }
    }

    private String safeJoin(String primary, String secondary) {
        if (StringUtils.hasText(primary) && StringUtils.hasText(secondary)) {
            return primary + "," + secondary;
        }
        if (StringUtils.hasText(primary)) {
            return primary;
        }
        if (StringUtils.hasText(secondary)) {
            return secondary;
        }
        return "";
    }

    private String normalizeBlank(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.strip();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record ProfilePromotionResult(String profileKey, String feedKey) {
    }

    private record SmokeCheck(boolean missingObject, boolean urlFailure) {
    }
}
