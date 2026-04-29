package com.example.media.service;

import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.strategy.PresignedUpload;
import com.example.cheerboard.storage.strategy.StorageStrategy;
import com.example.cheerboard.storage.strategy.StoredObject;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.InternalServerBusinessException;
import com.example.common.exception.NotFoundBusinessException;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaUploadService {

    private final MediaAssetRepository mediaAssetRepository;
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
     * NOTE: finalizeUpload는 의도적으로 메서드 레벨 @Transactional을 사용하지 않습니다.
     * 메서드 전체를 단일 트랜잭션으로 감싸면 OCI Object Storage의 download/upload/delete
     * 동기 호출(수백 ms)이 끝날 때까지 HikariCP 커넥션을 점유합니다. prod 풀(30)을 빠르게
     * 고갈시킬 수 있어, OCI I/O는 트랜잭션 밖에서 실행하고 DB 변경(findById/save)은
     * Spring Data JPA가 메서드별로 자동 생성하는 짧은 트랜잭션에 위임합니다.
     */
    public FinalizeMediaUploadResponse finalizeUpload(Long userId, Long assetId) {
        MediaAsset asset = mediaAssetRepository.findById(assetId)
                .orElseThrow(() -> new NotFoundBusinessException("MEDIA_ASSET_NOT_FOUND", "업로드 대상을 찾을 수 없습니다."));
        if (!asset.getOwnerUserId().equals(userId)) {
            throw new BadRequestBusinessException("MEDIA_ASSET_OWNER_MISMATCH", "다른 사용자의 업로드는 완료할 수 없습니다.");
        }

        if (asset.getStatus() == MediaAssetStatus.READY && asset.getObjectKey() != null) {
            return buildFinalizeResponse(asset);
        }
        if (asset.getStatus() != MediaAssetStatus.PENDING) {
            throw new BadRequestBusinessException("MEDIA_ASSET_INVALID_STATUS", "완료할 수 없는 업로드 상태입니다.");
        }

        String bucket = asset.getDomain().resolveBucket(storageConfig);
        String finalObjectKey = null;
        String feedObjectKey = null;
        try {
            Boolean exists = storageStrategy.exists(bucket, asset.getStagingObjectKey()).block();
            if (exists == null || !exists) {
                throw new NotFoundBusinessException("MEDIA_STAGING_OBJECT_NOT_FOUND", "업로드한 파일을 찾을 수 없습니다.");
            }

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
                case DIARY, CHEER, CHAT -> imageUtil.process(uploadedFile, metricSource);
            };
            ImageUtil.ImageDimension storedDimension = validationService.getActualDimension(processedImage.getBytes());
            finalObjectKey = asset.getDomain().buildFinalObjectKey(userId, asset.getId(), processedImage.getExtension());
            storageStrategy.uploadBytes(
                    processedImage.getBytes(),
                    processedImage.getContentType(),
                    bucket,
                    finalObjectKey).block();
            asset.markReady(
                    finalObjectKey,
                    processedImage.getContentType(),
                    processedImage.getSize(),
                    storedDimension.width(),
                    storedDimension.height());
            mediaAssetRepository.save(asset);

            if (asset.getDomain() == MediaDomain.PROFILE) {
                ImageUtil.ProcessedImage feedImage = imageUtil.processFeedProfileImage(uploadedFile, "media_profile_feed");
                ImageUtil.ImageDimension feedDimension = validationService.getActualDimension(feedImage.getBytes());
                feedObjectKey = asset.getDomain().buildProfileFeedObjectKey(userId, asset.getId(), feedImage.getExtension());
                storageStrategy.uploadBytes(feedImage.getBytes(), feedImage.getContentType(), bucket, feedObjectKey).block();

                MediaAsset feedAsset = mediaAssetRepository.findByDerivedFrom_Id(asset.getId())
                        .orElseGet(() -> MediaAsset.builder()
                                .ownerUserId(userId)
                                .domain(MediaDomain.PROFILE)
                                .status(MediaAssetStatus.PENDING)
                                .originalFileName(asset.getOriginalFileName())
                                .declaredContentType(asset.getDeclaredContentType())
                                .declaredBytes(asset.getDeclaredBytes())
                                .declaredWidth(asset.getDeclaredWidth())
                                .declaredHeight(asset.getDeclaredHeight())
                                .stagingObjectKey(asset.getStagingObjectKey())
                                .uploadExpiresAt(asset.getUploadExpiresAt())
                                .derivedFrom(asset)
                                .build());
                feedAsset.markReady(
                        feedObjectKey,
                        feedImage.getContentType(),
                        feedImage.getSize(),
                        feedDimension.width(),
                        feedDimension.height());
                mediaAssetRepository.save(feedAsset);
            }

            storageStrategy.delete(bucket, asset.getStagingObjectKey()).block();
            metricsService.recordMediaFinalize(asset.getDomain().name(), "success");
            return buildFinalizeResponse(asset);
        } catch (RuntimeException ex) {
            cleanupFailedFinalize(asset, finalObjectKey, feedObjectKey);
            metricsService.recordMediaFinalize(asset.getDomain().name(), "failure");
            throw ex;
        } catch (Exception ex) {
            cleanupFailedFinalize(asset, finalObjectKey, feedObjectKey);
            metricsService.recordMediaFinalize(asset.getDomain().name(), "failure");
            throw new InternalServerBusinessException("MEDIA_UPLOAD_FINALIZE_FAILED", "이미지 업로드 완료 처리에 실패했습니다.");
        }
    }

    @Transactional
    public void deleteUpload(Long userId, Long assetId) {
        MediaAsset asset = mediaAssetRepository.findById(assetId)
                .orElseThrow(() -> new NotFoundBusinessException("MEDIA_ASSET_NOT_FOUND", "업로드 대상을 찾을 수 없습니다."));
        if (!asset.getOwnerUserId().equals(userId)) {
            throw new BadRequestBusinessException("MEDIA_ASSET_OWNER_MISMATCH", "다른 사용자의 업로드는 삭제할 수 없습니다.");
        }
        if (mediaAssetLinkRepository.existsByAssetId(assetId)) {
            throw new BadRequestBusinessException("MEDIA_ASSET_LINKED", "이미 사용 중인 이미지는 삭제할 수 없습니다.");
        }

        deleteAssetObject(asset);
        mediaAssetRepository.findByDerivedFrom_Id(asset.getId()).ifPresent(this::deleteAssetObject);
    }

    @Transactional
    public MediaCleanupTargetReport cleanupExpiredPendingAssets() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(storageConfig.getMediaPendingRetentionHours());
        List<MediaAsset> expiredAssets = mediaAssetRepository.findByStatusAndUploadExpiresAtBefore(MediaAssetStatus.PENDING, cutoff);
        int deletedCount = 0;
        int errorCount = 0;
        for (MediaAsset asset : expiredAssets) {
            try {
                deleteAssetObject(asset);
                metricsService.recordMediaCleanup("pending", "deleted");
                deletedCount++;
            } catch (RuntimeException ex) {
                metricsService.recordMediaCleanup("pending", "error");
                log.warn("Expired pending media cleanup failed: assetId={}, cause={}", asset.getId(), ex.getMessage());
                errorCount++;
            }
        }
        return new MediaCleanupTargetReport(MediaCleanupTarget.PENDING, expiredAssets.size(), deletedCount, errorCount);
    }

    @Transactional
    public MediaCleanupTargetReport cleanupUnlinkedReadyAssets() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(storageConfig.getMediaOrphanRetentionHours());
        List<MediaAsset> orphanAssets = mediaAssetRepository.findUnlinkedAssetsOlderThan(MediaAssetStatus.READY, cutoff);
        int deletedCount = 0;
        int errorCount = 0;
        for (MediaAsset asset : orphanAssets) {
            try {
                asset.markOrphaned();
                mediaAssetRepository.save(asset);
                deleteAssetObject(asset);
                metricsService.recordMediaCleanup("orphan", "deleted");
                deletedCount++;
            } catch (RuntimeException ex) {
                metricsService.recordMediaCleanup("orphan", "error");
                log.warn("Orphan media cleanup failed: assetId={}, cause={}", asset.getId(), ex.getMessage());
                errorCount++;
            }
        }
        return new MediaCleanupTargetReport(MediaCleanupTarget.ORPHAN, orphanAssets.size(), deletedCount, errorCount);
    }

    private FinalizeMediaUploadResponse buildFinalizeResponse(MediaAsset asset) {
        String bucket = asset.getDomain().resolveBucket(storageConfig);
        String publicUrl = storageStrategy.getUrl(bucket, asset.getObjectKey(), storageConfig.getSignedUrlTtlSeconds()).block();
        if (publicUrl == null || publicUrl.isBlank()) {
            throw new InternalServerBusinessException("MEDIA_PUBLIC_URL_FAILED", "이미지 URL 생성에 실패했습니다.");
        }
        return new FinalizeMediaUploadResponse(asset.getId(), asset.getObjectKey(), publicUrl);
    }

    private void cleanupFailedFinalize(MediaAsset asset, String finalObjectKey, String feedObjectKey) {
        String bucket = asset.getDomain().resolveBucket(storageConfig);
        deleteQuietly(bucket, asset.getStagingObjectKey());
        deleteQuietly(bucket, finalObjectKey);
        deleteQuietly(bucket, feedObjectKey);
        asset.markDeleted();
        mediaAssetRepository.save(asset);
        mediaAssetRepository.findByDerivedFrom_Id(asset.getId()).ifPresent(feedAsset -> {
            feedAsset.markDeleted();
            mediaAssetRepository.save(feedAsset);
        });
    }

    private void deleteAssetObject(MediaAsset asset) {
        String bucket = asset.getDomain().resolveBucket(storageConfig);
        if (asset.getStatus() == MediaAssetStatus.PENDING) {
            deleteQuietly(bucket, asset.getStagingObjectKey());
        }
        if (asset.getObjectKey() != null && !asset.getObjectKey().isBlank()) {
            deleteQuietly(bucket, asset.getObjectKey());
        }
        asset.markDeleted();
        mediaAssetRepository.save(asset);
    }

    private void deleteQuietly(String bucket, String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            storageStrategy.delete(bucket, objectKey).block();
        } catch (Exception ex) {
            log.warn("Media object delete skipped: key={}, cause={}", objectKey, ex.getMessage());
        }
    }
}
