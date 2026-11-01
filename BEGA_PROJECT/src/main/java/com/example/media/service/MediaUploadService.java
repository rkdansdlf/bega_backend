package com.example.media.service;

import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.strategy.PresignedUpload;
import com.example.cheerboard.storage.strategy.StorageOperationException;
import com.example.cheerboard.storage.strategy.StorageStrategy;
import com.example.cheerboard.storage.strategy.StoredObject;
import com.example.cheerboard.storage.strategy.StoredObjectMetadata;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.InternalServerBusinessException;
import com.example.common.exception.NotFoundBusinessException;
import com.example.common.exception.ServiceUnavailableBusinessException;
import com.example.common.image.ImageOptimizationMetricsService;
import com.example.common.image.ImageUtil;
import com.example.media.dto.MediaCleanupTargetReport;
import com.example.media.dto.FinalizeMediaUploadResponse;
import com.example.media.dto.InitMediaUploadRequest;
import com.example.media.dto.InitMediaUploadResponse;
import com.example.media.entity.MediaAsset;
import com.example.media.entity.MediaAssetStatus;
import com.example.media.entity.MediaCleanupTarget;
import com.example.media.entity.MediaDomain;
import com.example.media.repository.MediaAssetLinkRepository;
import com.example.media.repository.MediaAssetRepository;
import com.example.media.support.ByteArrayMultipartFile;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaUploadService {

    private static final long STAGING_UPLOAD_QUIESCENCE_MINUTES = 15;

    private static final int ACCOUNT_ASSET_CLEANUP_BATCH_SIZE = 100;
    private static final Set<MediaDomain> ACCOUNT_FINALIZATION_MEDIA_DOMAINS = Set.of(
            MediaDomain.PROFILE,
            MediaDomain.CHEER,
            MediaDomain.CHAT);

    private final MediaAssetRepository mediaAssetRepository;
    private final MediaFinalizeClaimService finalizeClaimService;
    private final MediaFinalizeAttemptCleanupService finalizeAttemptCleanupService;
    private final MediaAssetLinkRepository mediaAssetLinkRepository;
    private final StorageStrategy storageStrategy;
    private final StorageConfig storageConfig;
    private final MediaUploadValidationService validationService;
    private final MediaQuotaService mediaQuotaService;
    private final MediaRateLimitService mediaRateLimitService;
    private final ImageUtil imageUtil;
    private final ImageOptimizationMetricsService metricsService;

    @Transactional
    public InitMediaUploadResponse initUpload(Long userId, InitMediaUploadRequest request) {
        mediaRateLimitService.assertUploadAllowed(userId, request.domain());
        validationService.validateInitRequest(request);
        mediaQuotaService.assertWithinQuota(userId, request.domain(), request.contentLength());
        metricsService.recordMediaInit(request.domain().name());

        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(storageConfig.getMediaUploadUrlTtlSeconds());
        MediaAsset asset = mediaAssetRepository.saveAndFlush(MediaAsset.builder()
                .ownerUserId(userId)
                .domain(request.domain())
                .status(MediaAssetStatus.PENDING)
                .originalFileName(request.fileName())
                .declaredContentType(request.contentType())
                .declaredBytes(request.contentLength())
                .declaredWidth(request.width())
                .declaredHeight(request.height())
                .stagingObjectKey("pending")
                .uploadExpiresAt(expiresAt)
                .build());

        String stagingObjectKey = request.domain().buildStagingObjectKey(userId, asset.getId(), request.fileName());
        asset.setStagingObjectKey(stagingObjectKey);
        mediaAssetRepository.save(asset);

        PresignedUpload presignedUpload = storageStrategy
                .presignPut(
                        request.domain().resolveBucket(storageConfig),
                        stagingObjectKey,
                        request.contentType(),
                        request.contentLength(),
                        storageConfig.getMediaUploadUrlTtlSeconds())
                .block();
        if (presignedUpload == null || presignedUpload.url() == null || presignedUpload.url().isBlank()) {
            throw new InternalServerBusinessException("MEDIA_UPLOAD_INIT_FAILED", "업로드 URL 생성에 실패했습니다.");
        }

        return new InitMediaUploadResponse(
                asset.getId(),
                presignedUpload.url(),
                stagingObjectKey,
                expiresAt.toInstant(ZoneOffset.UTC),
                presignedUpload.requiredHeaders());
    }

    /**
     * OCI I/O는 짧은 claim/commit 트랜잭션 사이에서 실행해 DB 커넥션을 점유하지 않습니다.
     */
    public FinalizeMediaUploadResponse finalizeUpload(Long userId, Long assetId) {
        MediaAsset existingAsset = mediaAssetRepository.findByIdAndOwnerUserId(assetId, userId)
                .orElseThrow(() -> new NotFoundBusinessException("MEDIA_ASSET_NOT_FOUND", "업로드 대상을 찾을 수 없습니다."));

        if (existingAsset.getStatus() == MediaAssetStatus.READY && existingAsset.getObjectKey() != null) {
            return buildFinalizeResponse(existingAsset);
        }
        if (existingAsset.getStatus() != MediaAssetStatus.PENDING
                && existingAsset.getStatus() != MediaAssetStatus.FINALIZING) {
            throw new BadRequestBusinessException("MEDIA_ASSET_INVALID_STATUS", "완료할 수 없는 업로드 상태입니다.");
        }

        String bucket = existingAsset.getDomain().resolveBucket(storageConfig);
        MediaFinalizeClaimService.FinalizeClaim claim = finalizeClaimService
                .claim(userId, assetId, bucket)
                .orElse(null);
        if (claim == null) {
            MediaAsset latestAsset = mediaAssetRepository.findByIdAndOwnerUserId(assetId, userId)
                    .orElseThrow(() -> new NotFoundBusinessException(
                            "MEDIA_ASSET_NOT_FOUND", "업로드 대상을 찾을 수 없습니다."));
            if (latestAsset.getStatus() == MediaAssetStatus.READY && latestAsset.getObjectKey() != null) {
                return buildFinalizeResponse(latestAsset);
            }
            if (latestAsset.getStatus() == MediaAssetStatus.PENDING
                    || latestAsset.getStatus() == MediaAssetStatus.FINALIZING) {
                throw new ServiceUnavailableBusinessException(
                        "MEDIA_FINALIZATION_IN_PROGRESS", "이미지 처리가 진행 중입니다. 잠시 후 다시 시도해 주세요.");
            }
            throw new BadRequestBusinessException("MEDIA_ASSET_INVALID_STATUS", "완료할 수 없는 업로드 상태입니다.");
        }

        MediaAsset asset = claim.asset();
        String finalObjectKey = null;
        String feedObjectKey = null;
        try {
            boolean previousObjectsDeleted = tryDelete(bucket, asset.getFinalizeObjectKey());
            previousObjectsDeleted &= tryDelete(bucket, asset.getFinalizeFeedObjectKey());
            if (!previousObjectsDeleted) {
                throw new ServiceUnavailableBusinessException(
                        "MEDIA_STORAGE_TEMPORARILY_UNAVAILABLE", "이전 이미지 처리 정리 중입니다. 잠시 후 다시 시도해 주세요.");
            }
            if ((asset.getFinalizeObjectKey() != null || asset.getFinalizeFeedObjectKey() != null)
                    && !finalizeClaimService.clearObjectKeys(claim)) {
                throw finalizeClaimLost();
            }
            if (!finalizeClaimService.renew(claim)) {
                throw finalizeClaimLost();
            }

            Boolean exists = storageStrategy.exists(bucket, asset.getStagingObjectKey()).block();
            if (exists == null || !exists) {
                throw new NotFoundBusinessException("MEDIA_STAGING_OBJECT_NOT_FOUND", "업로드한 파일을 찾을 수 없습니다.");
            }

            StoredObjectMetadata metadata = storageStrategy.head(bucket, asset.getStagingObjectKey()).block();
            validationService.validateStoredObjectMetadata(asset, metadata);

            StoredObject storedObject = storageStrategy.download(bucket, asset.getStagingObjectKey()).block();
            if (storedObject == null || storedObject.bytes() == null || storedObject.bytes().length == 0) {
                throw new BadRequestBusinessException("MEDIA_STAGING_OBJECT_EMPTY", "업로드한 파일이 비어 있습니다.");
            }

            String actualContentType = storedObject.contentType();
            ByteArrayMultipartFile uploadedFile = new ByteArrayMultipartFile(
                    asset.getOriginalFileName(),
                    actualContentType != null && !actualContentType.isBlank() ? actualContentType : asset.getDeclaredContentType(),
                    storedObject.bytes());

            ImageUtil.ImageDimension actualDimension = validationService.getActualDimension(storedObject.bytes());
            validationService.validateDeclaredMatchesActual(asset, actualDimension, storedObject.size(), uploadedFile.getContentType());
            validationService.validateFinalizedUpload(asset.getDomain(), uploadedFile);

            String metricSource = "media_" + asset.getDomain().getPathSegment();
            ImageUtil.ProcessedImage processedImage = switch (asset.getDomain()) {
                case PROFILE -> imageUtil.processProfileImage(uploadedFile, metricSource);
                case DIARY, SEAT_VIEW, CHEER, CHAT -> imageUtil.process(uploadedFile, metricSource);
            };
            ImageUtil.ImageDimension storedDimension = validationService.getActualDimension(processedImage.getBytes());
            finalObjectKey = asset.getDomain().buildFinalObjectKey(
                    userId, asset.getId(), claim.token(), processedImage.getExtension());

            ImageUtil.ProcessedImage feedImage = null;
            ImageUtil.ImageDimension feedDimension = null;
            if (asset.getDomain() == MediaDomain.PROFILE) {
                feedImage = imageUtil.processFeedProfileImage(uploadedFile, "media_profile_feed");
                feedDimension = validationService.getActualDimension(feedImage.getBytes());
                feedObjectKey = asset.getDomain().buildProfileFeedObjectKey(
                        userId, asset.getId(), claim.token(), feedImage.getExtension());
            }

            if (!finalizeClaimService.recordObjectKeys(claim, finalObjectKey, feedObjectKey)
                    || !finalizeClaimService.renew(claim)) {
                throw finalizeClaimLost();
            }
            storageStrategy.uploadBytes(
                    processedImage.getBytes(),
                    processedImage.getContentType(),
                    bucket,
                    finalObjectKey).block();
            if (feedImage != null) {
                storageStrategy.uploadBytes(feedImage.getBytes(), feedImage.getContentType(), bucket, feedObjectKey).block();
            }

            if (!finalizeClaimService.renew(claim)) {
                throw finalizeClaimLost();
            }

            MediaFinalizeClaimService.FinalizedObject primary = new MediaFinalizeClaimService.FinalizedObject(
                    finalObjectKey,
                    processedImage.getContentType(),
                    processedImage.getSize(),
                    storedDimension.width(),
                    storedDimension.height());
            MediaFinalizeClaimService.FinalizedObject feed = feedImage == null
                    ? null
                    : new MediaFinalizeClaimService.FinalizedObject(
                            feedObjectKey,
                            feedImage.getContentType(),
                            feedImage.getSize(),
                            feedDimension.width(),
                            feedDimension.height());
            asset = finalizeClaimService.complete(claim, primary, feed).orElseThrow(this::finalizeClaimLost);
        } catch (StorageOperationException ex) {
            cleanupFinalizeAttempt(claim);
            finalizeClaimService.release(claim);
            if (ex.isObjectNotFound()) {
                metricsService.recordMediaFinalize(asset.getDomain().name(), "failure");
                throw new NotFoundBusinessException("MEDIA_STAGING_OBJECT_NOT_FOUND", "업로드한 파일을 찾을 수 없습니다.");
            }
            metricsService.recordMediaFinalize(asset.getDomain().name(), "storage_unavailable");
            log.warn("Media finalize storage failure, keeping PENDING: assetId={}, kind={}, cause={}",
                    asset.getId(), ex.getKind(), ex.getMessage());
            if (ex.getKind() == StorageOperationException.Kind.TRANSIENT) {
                throw new ServiceUnavailableBusinessException(
                        "MEDIA_STORAGE_TEMPORARILY_UNAVAILABLE", "이미지 저장소가 일시적으로 응답하지 않습니다. 잠시 후 다시 시도해 주세요.");
            }
            throw new InternalServerBusinessException("MEDIA_STORAGE_FAILURE", "이미지 저장소 처리에 실패했습니다.");
        } catch (RuntimeException ex) {
            cleanupFinalizeAttempt(claim);
            finalizeClaimService.release(claim);
            metricsService.recordMediaFinalize(asset.getDomain().name(), "failure");
            throw ex;
        } catch (Exception ex) {
            cleanupFinalizeAttempt(claim);
            finalizeClaimService.release(claim);
            metricsService.recordMediaFinalize(asset.getDomain().name(), "failure");
            throw new InternalServerBusinessException("MEDIA_UPLOAD_FINALIZE_FAILED", "이미지 업로드 완료 처리에 실패했습니다.");
        }

        // DB가 READY로 커밋된 뒤에만 공유 staging 객체를 지운다. 실패하면 별도 cleanup이 재시도한다.
        // The presigned PUT may still be in flight or replayed until its expiry. Keep the
        // staging key for the scheduled cleanup even if this eager delete currently succeeds.
        deleteAndConfirmAbsent(bucket, asset.getStagingObjectKey());
        metricsService.recordMediaFinalize(asset.getDomain().name(), "success");
        return buildFinalizeResponse(asset);
    }

    @Transactional
    public void unlinkEntityMedia(MediaDomain domain, Long entityId) {
        if (domain != null && entityId != null) {
            mediaAssetLinkRepository.deleteByDomainAndEntityId(domain, entityId);
        }
    }

    public boolean hasPendingCheerPurge(Long userId) {
        return mediaAssetRepository.existsByOwnerUserIdAndStatus(userId, MediaAssetStatus.CHEER_PURGE_PENDING);
    }

    /**
     * Reserves owned objects for deletion after the surrounding account transaction commits.
     * Returns how many owned assets could not be claimed for a reason other than a cheer purge,
     * so the caller can keep the account's cleanup retry pending instead of reporting completion.
     */
    @Transactional
    public int deleteOwnedAssetsForAccount(Long userId, Set<Long> protectedCheerPostIds) {
        if (userId == null) {
            throw new IllegalArgumentException("A persisted account ID is required for media cleanup.");
        }
        Set<Long> protectedIds = protectedCheerPostIds == null ? Set.of() : protectedCheerPostIds;
        int unclaimedAssets = 0;
        long afterId = 0L;
        List<MediaAsset> batch;
        do {
            batch = mediaAssetRepository.findByOwnerUserIdAndIdGreaterThanOrderByIdAsc(
                    userId, afterId, PageRequest.of(0, ACCOUNT_ASSET_CLEANUP_BATCH_SIZE));
            for (MediaAsset asset : batch) {
                afterId = asset.getId();
                if (!userId.equals(asset.getOwnerUserId())
                        || !ACCOUNT_FINALIZATION_MEDIA_DOMAINS.contains(asset.getDomain())) {
                    continue;
                }
                MediaAsset derivedAsset = mediaAssetRepository.findByDerivedFrom_Id(asset.getId()).orElse(null);
                if (derivedAsset != null
                        && userId.equals(derivedAsset.getOwnerUserId())
                        && !ACCOUNT_FINALIZATION_MEDIA_DOMAINS.contains(derivedAsset.getDomain())) {
                    continue;
                }
                if (hasProtectedCheerLink(asset, protectedIds)
                        || (asset.getDerivedFrom() != null && hasProtectedCheerLink(asset.getDerivedFrom(), protectedIds))
                        || (derivedAsset != null && hasProtectedCheerLink(derivedAsset, protectedIds))) {
                    continue;
                }
                MediaFinalizeClaimService.FinalizeCleanupClaim cleanupClaim = finalizeClaimService
                        .claimOwnedAssetForAccountDeletion(asset, userId)
                        .orElse(null);
                if (cleanupClaim == null) {
                    failIfCheerPurgePending(asset.getId(), userId);
                    unclaimedAssets++;
                    continue;
                }
                mediaAssetLinkRepository.deleteByAssetId(asset.getId());
                // The parent claim fences a profile finalize from publishing a new derivative.
                // Re-read after the claim to catch a derivative committed between the page scan
                // and the parent claim.
                MediaAsset derivedAssetToDelete = mediaAssetRepository.findByDerivedFrom_Id(asset.getId())
                        .orElse(derivedAsset);
                if (derivedAssetToDelete != null
                        && userId.equals(derivedAssetToDelete.getOwnerUserId())
                        && ACCOUNT_FINALIZATION_MEDIA_DOMAINS.contains(derivedAssetToDelete.getDomain())
                        && !hasProtectedCheerLink(derivedAssetToDelete, protectedIds)) {
                    MediaFinalizeClaimService.FinalizeCleanupClaim derivedCleanupClaim = finalizeClaimService
                            .claimOwnedAssetForAccountDeletion(derivedAssetToDelete, userId)
                            .orElse(null);
                    if (derivedCleanupClaim != null) {
                        mediaAssetLinkRepository.deleteByAssetId(derivedAssetToDelete.getId());
                    } else {
                        failIfCheerPurgePending(derivedAssetToDelete.getId(), userId);
                        unclaimedAssets++;
                    }
                }
            }
        } while (batch.size() == ACCOUNT_ASSET_CLEANUP_BATCH_SIZE);
        return unclaimedAssets;
    }

    private void failIfCheerPurgePending(Long assetId, Long userId) {
        if (mediaAssetRepository.findByIdAndOwnerUserId(assetId, userId)
                .filter(asset -> asset.getStatus() == MediaAssetStatus.CHEER_PURGE_PENDING).isPresent()) {
            throw new ServiceUnavailableBusinessException(
                    "MEDIA_DELETION_IN_PROGRESS", "게시글 이미지 정리가 진행 중입니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private boolean hasProtectedCheerLink(MediaAsset asset, Set<Long> protectedCheerPostIds) {
        return mediaAssetLinkRepository.findById(asset.getId())
                .filter(link -> link.getDomain() == MediaDomain.CHEER)
                .map(link -> protectedCheerPostIds.contains(link.getEntityId()))
                .orElse(false);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deleteUpload(Long userId, Long assetId) {
        MediaAsset asset = mediaAssetRepository.findByIdAndOwnerUserId(assetId, userId)
                .orElseThrow(() -> new NotFoundBusinessException("MEDIA_ASSET_NOT_FOUND", "업로드 대상을 찾을 수 없습니다."));
        if (mediaAssetLinkRepository.existsByAssetId(assetId)) {
            throw new BadRequestBusinessException("MEDIA_ASSET_LINKED", "이미 사용 중인 이미지는 삭제할 수 없습니다.");
        }

        MediaFinalizeClaimService.FinalizeCleanupClaim cleanupClaim = finalizeClaimService
                .claimOwnedAssetForDeletion(asset, userId)
                .orElseThrow(() -> new ServiceUnavailableBusinessException(
                        "MEDIA_DELETION_IN_PROGRESS", "이미지 삭제 처리가 진행 중입니다. 잠시 후 다시 시도해 주세요."));
        MediaAsset derivedAsset = mediaAssetRepository.findByDerivedFrom_Id(assetId).orElse(null);
        MediaFinalizeClaimService.FinalizeCleanupClaim derivedCleanupClaim = derivedAsset == null
                ? null
                : finalizeClaimService.claimOwnedAssetForDeletion(derivedAsset, userId).orElse(null);

        mediaAssetLinkRepository.deleteByAssetId(assetId);
        boolean allAbsent = cleanupTombstoneObjects(cleanupClaim);
        if (derivedCleanupClaim != null) {
            mediaAssetLinkRepository.deleteByAssetId(derivedAsset.getId());
            allAbsent &= cleanupTombstoneObjects(derivedCleanupClaim);
        }
        if (!allAbsent) {
            log.warn("Media upload delete left a retryable tombstone: assetId={}", assetId);
            throw new ServiceUnavailableBusinessException(
                    "MEDIA_STORAGE_TEMPORARILY_UNAVAILABLE", "이미지 삭제에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    public MediaCleanupTargetReport cleanupExpiredPendingAssets() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(storageConfig.getMediaPendingRetentionHours());
        List<MediaAsset> expiredAssets = mediaAssetRepository.findByStatusAndUploadExpiresAtBeforeOrderByUploadExpiresAtAscIdAsc(
                MediaAssetStatus.PENDING,
                cutoff,
                cleanupPageRequest());
        int deletedCount = 0;
        int errorCount = 0;
        for (MediaAsset asset : expiredAssets) {
            MediaFinalizeClaimService.FinalizeCleanupClaim cleanupClaim = finalizeClaimService
                    .claimExpiredPendingForCleanup(asset, LocalDateTime.now(), cutoff)
                    .orElse(null);
            if (cleanupClaim == null) {
                continue;
            }
            if (cleanupExpiredFinalizeObjects(asset, cleanupClaim)) {
                metricsService.recordMediaCleanup("pending", "deleted");
                deletedCount++;
            } else {
                metricsService.recordMediaCleanup("pending", "error");
                log.warn("Expired pending media cleanup failed: assetId={}", asset.getId());
                errorCount++;
            }
        }

        List<MediaAsset> expiredFinalizingAssets = mediaAssetRepository
                        .findByStatusAndFinalizeLeaseUntilBeforeAndUploadExpiresAtBeforeOrderByFinalizeLeaseUntilAscUploadExpiresAtAscIdAsc(
                        MediaAssetStatus.FINALIZING,
                        LocalDateTime.now(),
                        cutoff,
                        cleanupPageRequest());
        for (MediaAsset asset : expiredFinalizingAssets) {
            MediaFinalizeClaimService.FinalizeCleanupClaim cleanupClaim = finalizeClaimService
                    .claimExpiredForCleanup(asset, LocalDateTime.now(), cutoff)
                    .orElse(null);
            if (cleanupClaim == null) {
                continue;
            }
            if (cleanupExpiredFinalizeObjects(asset, cleanupClaim)) {
                metricsService.recordMediaCleanup("pending", "deleted");
                deletedCount++;
            } else {
                metricsService.recordMediaCleanup("pending", "error");
                log.warn("Expired media finalize cleanup failed: assetId={}", asset.getId());
                errorCount++;
            }
        }
        return new MediaCleanupTargetReport(
                MediaCleanupTarget.PENDING, expiredAssets.size() + expiredFinalizingAssets.size(), deletedCount, errorCount);
    }

    private boolean cleanupExpiredFinalizeObjects(
            MediaAsset asset,
            MediaFinalizeClaimService.FinalizeCleanupClaim cleanupClaim) {
        String bucket = asset.getDomain().resolveBucket(storageConfig);
        boolean allDeleted = deleteAndConfirmAbsent(bucket, asset.getStagingObjectKey());
        allDeleted &= deleteAndConfirmAbsent(bucket, asset.getFinalizeObjectKey());
        allDeleted &= deleteAndConfirmAbsent(bucket, asset.getFinalizeFeedObjectKey());
        allDeleted &= finalizeAttemptCleanupService.cleanupForAsset(asset.getId());
        return allDeleted && finalizeClaimService.markExpiredCleanupComplete(cleanupClaim);
    }

    public void cleanupReadyStagingObjects() {
        cleanupReadyStagingObjects(LocalDateTime.now());
    }

    void cleanupReadyStagingObjects(LocalDateTime now) {
        LocalDateTime eligibleBefore = now.minusMinutes(STAGING_UPLOAD_QUIESCENCE_MINUTES);
        long afterId = 0L;
        List<MediaAsset> batch;
        do {
            batch = mediaAssetRepository
                    .findByStatusAndStagingObjectKeyIsNotNullAndStagingObjectKeyNotAndUploadExpiresAtBeforeAndIdGreaterThanOrderByIdAsc(
                            MediaAssetStatus.READY,
                            MediaAsset.CLEARED_STAGING_OBJECT_KEY,
                            eligibleBefore,
                            afterId,
                            cleanupPageRequest());
            for (MediaAsset asset : batch) {
                afterId = asset.getId();
                String stagingKey = asset.getStagingObjectKey();
                if (deleteAndConfirmAbsent(asset.getDomain().resolveBucket(storageConfig), stagingKey)) {
                    mediaAssetRepository.clearReadyStagingObjectKey(
                            asset.getId(),
                            stagingKey,
                            MediaAsset.CLEARED_STAGING_OBJECT_KEY,
                            now,
                            eligibleBefore);
                } else {
                    log.warn("Ready media staging cleanup will retry: assetId={}", asset.getId());
                }
            }
        } while (batch.size() == Math.max(1, storageConfig.getMediaCleanupBatchSize()));
    }

    public int cleanupExpiredFinalizeAttempts() {
        return finalizeAttemptCleanupService.cleanupDueAttempts();
    }

    public void cleanupDeletedFinalizeTombstones() {
        List<MediaAsset> tombstones = mediaAssetRepository
                .findByStatusAndFinalizeTokenIsNotNullAndFinalizeLeaseUntilBeforeOrderByFinalizeLeaseUntilAscIdAsc(
                        MediaAssetStatus.DELETED, LocalDateTime.now(), cleanupPageRequest());
        for (MediaAsset asset : tombstones) {
            MediaFinalizeClaimService.FinalizeCleanupClaim cleanupClaim = finalizeClaimService
                    .claimDeletedTombstoneForCleanup(asset, LocalDateTime.now())
                    .orElse(null);
            if (cleanupClaim == null) {
                continue;
            }
            String bucket = asset.getDomain().resolveBucket(storageConfig);
            boolean allAbsent = deleteAndConfirmAbsent(bucket, asset.getStagingObjectKey());
            allAbsent &= deleteAndConfirmAbsent(bucket, asset.getObjectKey());
            allAbsent &= deleteAndConfirmAbsent(bucket, asset.getFinalizeObjectKey());
            allAbsent &= deleteAndConfirmAbsent(bucket, asset.getFinalizeFeedObjectKey());
            allAbsent &= finalizeAttemptCleanupService.cleanupForAsset(asset.getId());
            if (allAbsent) {
                if (asset.getFinalizeCleanupVerifiedAt() == null) {
                    finalizeClaimService.updateDeletedTombstoneVerification(cleanupClaim, LocalDateTime.now());
                } else {
                    finalizeClaimService.clearDeletedTombstone(cleanupClaim);
                }
            } else {
                finalizeClaimService.updateDeletedTombstoneVerification(cleanupClaim, null);
            }
        }
    }

    @Transactional
    public MediaCleanupTargetReport cleanupUnlinkedReadyAssets() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(storageConfig.getMediaOrphanRetentionHours());
        List<MediaAsset> orphanAssets = mediaAssetRepository.findUnlinkedAssetsOlderThan(
                MediaAssetStatus.READY,
                cutoff,
                cleanupPageRequest());
        int deletedCount = 0;
        int errorCount = 0;
        for (MediaAsset asset : orphanAssets) {
            if (asset.getDomain() != null) {
                try {
                    var claim = finalizeClaimService.claimUnlinkedMediaForCleanup(
                            asset.getId(), asset.getOwnerUserId(), asset.getDomain(), cutoff).orElse(null);
                    if (claim == null) continue;
                    if (cleanupTombstoneObjects(claim)) {
                        metricsService.recordMediaCleanup("orphan", "deleted");
                        deletedCount++;
                    } else {
                        metricsService.recordMediaCleanup("orphan", "error");
                        errorCount++;
                    }
                } catch (RuntimeException ex) {
                    metricsService.recordMediaCleanup("orphan", "error");
                    log.warn("Media orphan cleanup failed: assetId={}, cause={}", asset.getId(), ex.getMessage());
                    errorCount++;
                }
                continue;
            }
            MediaAssetStatus previousStatus = asset.getStatus();
            try {
                asset.markOrphaned();
                mediaAssetRepository.save(asset);
                deleteAssetObject(asset);
                metricsService.recordMediaCleanup("orphan", "deleted");
                deletedCount++;
            } catch (RuntimeException ex) {
                // 객체 삭제에 실패했으면 ORPHANED로 남기지 않는다. READY 조회 대상에서 빠져 다시 시도되지 않는다.
                asset.setStatus(previousStatus);
                metricsService.recordMediaCleanup("orphan", "error");
                log.warn("Orphan media cleanup failed: assetId={}, cause={}", asset.getId(), ex.getMessage());
                errorCount++;
            }
        }
        return new MediaCleanupTargetReport(MediaCleanupTarget.ORPHAN, orphanAssets.size(), deletedCount, errorCount);
    }

    private PageRequest cleanupPageRequest() {
        return PageRequest.of(0, Math.max(1, storageConfig.getMediaCleanupBatchSize()));
    }

    private FinalizeMediaUploadResponse buildFinalizeResponse(MediaAsset asset) {
        if (asset.getDomain() == MediaDomain.SEAT_VIEW) {
            return new FinalizeMediaUploadResponse(asset.getId(), asset.getObjectKey(), null);
        }
        String bucket = asset.getDomain().resolveBucket(storageConfig);
        String publicUrl = storageStrategy.getUrl(bucket, asset.getObjectKey(), storageConfig.getSignedUrlTtlSeconds()).block();
        if (publicUrl == null || publicUrl.isBlank()) {
            throw new InternalServerBusinessException("MEDIA_PUBLIC_URL_FAILED", "이미지 URL 생성에 실패했습니다.");
        }
        return new FinalizeMediaUploadResponse(asset.getId(), asset.getObjectKey(), publicUrl);
    }

    private void cleanupFinalizeAttempt(
            MediaFinalizeClaimService.FinalizeClaim claim) {
        finalizeAttemptCleanupService.cleanupAfterFailedFinalize(claim);
    }

    private ServiceUnavailableBusinessException finalizeClaimLost() {
        return new ServiceUnavailableBusinessException(
                "MEDIA_FINALIZATION_CLAIM_LOST", "이미지 처리 권한이 만료되었습니다. 다시 시도해 주세요.");
    }

    /** 객체 삭제에 실패하면 {@link StorageOperationException}이 전파되어 markDeleted까지 가지 않는다. */
    private void deleteAssetObject(MediaAsset asset) {
        String bucket = asset.getDomain().resolveBucket(storageConfig);
        boolean finalizationWasActive = asset.getStatus() == MediaAssetStatus.FINALIZING;
        deleteObject(bucket, asset.getStagingObjectKey());
        if (asset.getObjectKey() != null && !asset.getObjectKey().isBlank()) {
            deleteObject(bucket, asset.getObjectKey());
        }
        deleteObject(bucket, asset.getFinalizeObjectKey());
        deleteObject(bucket, asset.getFinalizeFeedObjectKey());
        if (finalizationWasActive) {
            asset.setStatus(MediaAssetStatus.DELETED);
            asset.setFinalizeLeaseUntil(LocalDateTime.now().plusMinutes(15));
            asset.setFinalizeCleanupVerifiedAt(null);
        } else {
            asset.markDeleted();
        }
        mediaAssetRepository.save(asset);
    }

    private void deleteObject(String bucket, String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        storageStrategy.deleteChecked(bucket, objectKey).block();
    }

    /** @return 삭제됐거나 지울 것이 없으면 true, 실패하면 false */
    private boolean tryDelete(String bucket, String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return true;
        }
        try {
            storageStrategy.deleteChecked(bucket, objectKey).block();
            return true;
        } catch (Exception ex) {
            log.warn("Media object delete failed: key={}, cause={}", objectKey, ex.getMessage());
            return false;
        }
    }

    private boolean deleteAndConfirmAbsent(String bucket, String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return true;
        }
        if (!tryDelete(bucket, objectKey)) {
            return false;
        }
        try {
            return Boolean.FALSE.equals(storageStrategy.exists(bucket, objectKey).block());
        } catch (Exception ex) {
            log.warn("Media object absence check failed: key={}, cause={}", objectKey, ex.getMessage());
            return false;
        }
    }

    private boolean cleanupTombstoneObjects(MediaFinalizeClaimService.FinalizeCleanupClaim claim) {
        MediaAsset asset = claim.asset();
        String bucket = asset.getDomain().resolveBucket(storageConfig);
        boolean allAbsent = deleteAndConfirmAbsent(bucket, asset.getStagingObjectKey());
        allAbsent &= deleteAndConfirmAbsent(bucket, asset.getObjectKey());
        allAbsent &= deleteAndConfirmAbsent(bucket, asset.getFinalizeObjectKey());
        allAbsent &= deleteAndConfirmAbsent(bucket, asset.getFinalizeFeedObjectKey());
        allAbsent &= finalizeAttemptCleanupService.cleanupForAsset(asset.getId());
        finalizeClaimService.updateDeletedTombstoneVerification(
                claim, allAbsent ? LocalDateTime.now() : null);
        return allAbsent;
    }
}
