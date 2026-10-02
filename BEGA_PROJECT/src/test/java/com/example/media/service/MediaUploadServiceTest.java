package com.example.media.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.strategy.StorageOperationException;
import com.example.cheerboard.storage.strategy.StorageStrategy;
import com.example.cheerboard.storage.strategy.StoredObject;
import com.example.cheerboard.storage.strategy.StoredObjectMetadata;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.NotFoundBusinessException;
import com.example.common.exception.ServiceUnavailableBusinessException;
import com.example.common.image.ImageOptimizationMetricsService;
import com.example.common.image.ImageUtil;
import com.example.media.dto.MediaCleanupTargetReport;
import com.example.media.dto.FinalizeMediaUploadResponse;
import com.example.media.entity.MediaAsset;
import com.example.media.entity.MediaAssetStatus;
import com.example.media.entity.MediaCleanupTarget;
import com.example.media.entity.MediaDomain;
import com.example.media.repository.MediaAssetLinkRepository;
import com.example.media.repository.MediaAssetRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class MediaUploadServiceTest {

    @InjectMocks
    private MediaUploadService mediaUploadService;

    @Mock
    private MediaAssetRepository mediaAssetRepository;

    @Mock
    private MediaAssetLinkRepository mediaAssetLinkRepository;

    @Mock
    private StorageStrategy storageStrategy;

    @Mock
    private StorageConfig storageConfig;

    @Mock
    private MediaUploadValidationService validationService;

    @Mock
    private MediaQuotaService mediaQuotaService;

    @Mock
    private MediaRateLimitService mediaRateLimitService;

    @Mock
    private ImageUtil imageUtil;

    @Mock
    private ImageOptimizationMetricsService metricsService;

    @Test
    @DisplayName("media finalize는 프로필 원본과 feed derivative를 함께 READY로 만든다")
    void finalizeUpload_profileCreatesPrimaryAndFeedAssets() throws Exception {
        byte[] originalBytes = new byte[] {1, 2, 3};
        byte[] optimizedBytes = new byte[] {4, 5};
        byte[] feedBytes = new byte[] {6};
        MediaAsset asset = MediaAsset.builder()
                .id(11L)
                .ownerUserId(7L)
                .domain(MediaDomain.PROFILE)
                .status(MediaAssetStatus.PENDING)
                .originalFileName("avatar.png")
                .declaredContentType("image/png")
                .declaredBytes((long) originalBytes.length)
                .declaredWidth(800)
                .declaredHeight(800)
                .stagingObjectKey("media/staging/profile/7/11-avatar.png")
                .uploadExpiresAt(LocalDateTime.now().plusHours(1))
                .build();

        when(mediaAssetRepository.findByIdAndOwnerUserId(11L, 7L)).thenReturn(Optional.of(asset));
        when(mediaAssetRepository.findByDerivedFrom_Id(11L)).thenReturn(Optional.empty());
        when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageConfig.getProfileBucket()).thenReturn("profile-bucket");
        when(storageConfig.getSignedUrlTtlSeconds()).thenReturn(600);
        when(storageStrategy.exists("profile-bucket", asset.getStagingObjectKey())).thenReturn(Mono.just(true));
        when(storageStrategy.head("profile-bucket", asset.getStagingObjectKey()))
                .thenReturn(Mono.just(new StoredObjectMetadata((long) originalBytes.length, "image/png")));
        when(storageStrategy.download("profile-bucket", asset.getStagingObjectKey()))
                .thenReturn(Mono.just(new StoredObject(originalBytes, "image/png")));
        when(validationService.getActualDimension(originalBytes)).thenReturn(new ImageUtil.ImageDimension(800, 800));
        when(validationService.getActualDimension(optimizedBytes)).thenReturn(new ImageUtil.ImageDimension(512, 512));
        when(validationService.getActualDimension(feedBytes)).thenReturn(new ImageUtil.ImageDimension(160, 160));
        when(imageUtil.processProfileImage(any(), eq("media_profile")))
                .thenReturn(new ImageUtil.ProcessedImage(optimizedBytes, "image/webp", "webp"));
        when(imageUtil.processFeedProfileImage(any(), eq("media_profile_feed")))
                .thenReturn(new ImageUtil.ProcessedImage(feedBytes, "image/webp", "webp"));
        when(storageStrategy.uploadBytes(any(), any(), eq("profile-bucket"), any())).thenReturn(Mono.just("ok"));
        when(storageStrategy.deleteChecked("profile-bucket", asset.getStagingObjectKey())).thenReturn(Mono.empty());
        when(storageStrategy.getUrl("profile-bucket", "media/profile/7/11.webp", 600))
                .thenReturn(Mono.just("https://signed.example/media/profile/7/11.webp"));

        FinalizeMediaUploadResponse response = mediaUploadService.finalizeUpload(7L, 11L);

        assertEquals("media/profile/7/11.webp", response.storagePath());
        assertEquals("https://signed.example/media/profile/7/11.webp", response.publicUrl());
        assertEquals(MediaAssetStatus.READY, asset.getStatus());
        assertEquals("media/profile/7/11.webp", asset.getObjectKey());

        ArgumentCaptor<MediaAsset> assetCaptor = ArgumentCaptor.forClass(MediaAsset.class);
        verify(mediaAssetRepository, times(2)).save(assetCaptor.capture());
        List<MediaAsset> savedAssets = assetCaptor.getAllValues();
        assertTrue(savedAssets.stream().anyMatch(saved -> "media/profile/7/11.webp".equals(saved.getObjectKey())));
        assertTrue(savedAssets.stream().anyMatch(saved ->
                "media/profile-feed/7/11.webp".equals(saved.getObjectKey())
                        && saved.getDerivedFrom() == asset
                        && saved.getStatus() == MediaAssetStatus.READY));
        verify(metricsService).recordMediaFinalize("PROFILE", "success");
    }

    @Test
    @DisplayName("media finalize 검증 실패 시 asset은 삭제 상태로 정리된다")
    void finalizeUpload_validationFailureMarksAssetDeleted() {
        byte[] originalBytes = new byte[] {1, 2, 3, 4};
        MediaAsset asset = MediaAsset.builder()
                .id(21L)
                .ownerUserId(9L)
                .domain(MediaDomain.DIARY)
                .status(MediaAssetStatus.PENDING)
                .originalFileName("diary.png")
                .declaredContentType("image/png")
                .declaredBytes((long) originalBytes.length)
                .declaredWidth(1200)
                .declaredHeight(900)
                .stagingObjectKey("media/staging/diary/9/21-diary.png")
                .uploadExpiresAt(LocalDateTime.now().plusHours(1))
                .build();

        when(mediaAssetRepository.findByIdAndOwnerUserId(21L, 9L)).thenReturn(Optional.of(asset));
        when(mediaAssetRepository.findByDerivedFrom_Id(21L)).thenReturn(Optional.empty());
        when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageConfig.getDiaryBucket()).thenReturn("diary-bucket");
        when(storageStrategy.exists("diary-bucket", asset.getStagingObjectKey())).thenReturn(Mono.just(true));
        when(storageStrategy.head("diary-bucket", asset.getStagingObjectKey()))
                .thenReturn(Mono.just(new StoredObjectMetadata((long) originalBytes.length, "image/png")));
        when(storageStrategy.download("diary-bucket", asset.getStagingObjectKey()))
                .thenReturn(Mono.just(new StoredObject(originalBytes, "image/png")));
        when(validationService.getActualDimension(originalBytes)).thenReturn(new ImageUtil.ImageDimension(1200, 900));
        when(storageStrategy.deleteChecked("diary-bucket", asset.getStagingObjectKey())).thenReturn(Mono.empty());
        doThrow(new BadRequestBusinessException("MEDIA_UPLOAD_METADATA_MISMATCH", "metadata mismatch"))
                .when(validationService)
                .validateDeclaredMatchesActual(asset, new ImageUtil.ImageDimension(1200, 900), (long) originalBytes.length, "image/png");

        BadRequestBusinessException exception = assertThrows(
                BadRequestBusinessException.class,
                () -> mediaUploadService.finalizeUpload(9L, 21L));

        assertEquals("MEDIA_UPLOAD_METADATA_MISMATCH", exception.getCode());
        assertEquals(MediaAssetStatus.DELETED, asset.getStatus());
        verify(metricsService).recordMediaFinalize("DIARY", "failure");
    }

    @Test
    @DisplayName("media finalize는 oversized staged object를 다운로드 전에 거부한다")
    void finalizeUpload_rejectsOversizedStagedObjectBeforeDownload() {
        MediaAsset asset = MediaAsset.builder()
                .id(22L)
                .ownerUserId(9L)
                .domain(MediaDomain.DIARY)
                .status(MediaAssetStatus.PENDING)
                .originalFileName("diary.png")
                .declaredContentType("image/png")
                .declaredBytes(20L * 1024L * 1024L)
                .declaredWidth(1200)
                .declaredHeight(900)
                .stagingObjectKey("media/staging/diary/9/22-diary.png")
                .uploadExpiresAt(LocalDateTime.now().plusHours(1))
                .build();
        StoredObjectMetadata metadata = new StoredObjectMetadata(20L * 1024L * 1024L, "image/png");

        when(mediaAssetRepository.findByIdAndOwnerUserId(22L, 9L)).thenReturn(Optional.of(asset));
        when(mediaAssetRepository.findByDerivedFrom_Id(22L)).thenReturn(Optional.empty());
        when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageConfig.getDiaryBucket()).thenReturn("diary-bucket");
        when(storageStrategy.exists("diary-bucket", asset.getStagingObjectKey())).thenReturn(Mono.just(true));
        when(storageStrategy.head("diary-bucket", asset.getStagingObjectKey())).thenReturn(Mono.just(metadata));
        when(storageStrategy.deleteChecked("diary-bucket", asset.getStagingObjectKey())).thenReturn(Mono.empty());
        doThrow(new BadRequestBusinessException("INVALID_MEDIA_FILE_SIZE", "too large"))
                .when(validationService)
                .validateStoredObjectMetadata(asset, metadata);

        BadRequestBusinessException exception = assertThrows(
                BadRequestBusinessException.class,
                () -> mediaUploadService.finalizeUpload(9L, 22L));

        assertEquals("INVALID_MEDIA_FILE_SIZE", exception.getCode());
        assertEquals(MediaAssetStatus.DELETED, asset.getStatus());
        verify(storageStrategy, never()).download(any(), any());
        verify(metricsService).recordMediaFinalize("DIARY", "failure");
    }

    @Test
    @DisplayName("media finalize는 타인 asset을 없는 업로드처럼 처리한다")
    void finalizeUpload_rejectsNonOwnedAssetAsNotFound() {
        when(mediaAssetRepository.findByIdAndOwnerUserId(31L, 8L)).thenReturn(Optional.empty());

        NotFoundBusinessException exception = assertThrows(
                NotFoundBusinessException.class,
                () -> mediaUploadService.finalizeUpload(8L, 31L));

        assertEquals("MEDIA_ASSET_NOT_FOUND", exception.getCode());
        verify(storageStrategy, never()).exists(any(), any());
    }

    @Test
    @DisplayName("media delete는 타인 asset을 없는 업로드처럼 처리하고 링크 검사 전에 멈춘다")
    void deleteUpload_rejectsNonOwnedAssetAsNotFound() {
        when(mediaAssetRepository.findByIdAndOwnerUserId(41L, 8L)).thenReturn(Optional.empty());

        NotFoundBusinessException exception = assertThrows(
                NotFoundBusinessException.class,
                () -> mediaUploadService.deleteUpload(8L, 41L));

        assertEquals("MEDIA_ASSET_NOT_FOUND", exception.getCode());
        verify(mediaAssetLinkRepository, never()).existsByAssetId(any());
        verify(storageStrategy, never()).deleteChecked(any(), any());
    }

    @Test
    @DisplayName("만료된 pending asset cleanup은 staging object를 지우고 삭제 상태로 전환한다")
    void cleanupExpiredPendingAssets_deletesPendingObject() {
        MediaAsset asset = MediaAsset.builder()
                .id(51L)
                .ownerUserId(3L)
                .domain(MediaDomain.CHEER)
                .status(MediaAssetStatus.PENDING)
                .stagingObjectKey("media/staging/cheer/3/51-photo.png")
                .uploadExpiresAt(LocalDateTime.now().minusDays(2))
                .build();

        when(storageConfig.getMediaPendingRetentionHours()).thenReturn(24);
        when(storageConfig.getMediaCleanupBatchSize()).thenReturn(100);
        when(storageConfig.getCheerBucket()).thenReturn("cheer-bucket");
        when(mediaAssetRepository.findByStatusAndUploadExpiresAtBeforeOrderByUploadExpiresAtAscIdAsc(
                eq(MediaAssetStatus.PENDING),
                any(LocalDateTime.class),
                eq(PageRequest.of(0, 100))))
                .thenReturn(List.of(asset));
        when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageStrategy.deleteChecked("cheer-bucket", asset.getStagingObjectKey())).thenReturn(Mono.empty());

        MediaCleanupTargetReport report = mediaUploadService.cleanupExpiredPendingAssets();

        assertEquals(MediaAssetStatus.DELETED, asset.getStatus());
        assertEquals(MediaCleanupTarget.PENDING, report.target());
        assertEquals(1, report.scannedCount());
        assertEquals(1, report.deletedCount());
        assertEquals(0, report.errorCount());
        verify(storageStrategy).deleteChecked("cheer-bucket", asset.getStagingObjectKey());
        verify(metricsService).recordMediaCleanup("pending", "deleted");
        verify(mediaAssetRepository).findByStatusAndUploadExpiresAtBeforeOrderByUploadExpiresAtAscIdAsc(
                eq(MediaAssetStatus.PENDING),
                any(LocalDateTime.class),
                eq(PageRequest.of(0, 100)));
    }

    @Test
    @DisplayName("미연결 READY asset cleanup은 orphan 표시 후 object를 지우고 삭제 상태로 전환한다")
    void cleanupUnlinkedReadyAssets_marksOrphanThenDeletesObject() {
        MediaAsset asset = MediaAsset.builder()
                .id(61L)
                .ownerUserId(4L)
                .domain(MediaDomain.CHAT)
                .status(MediaAssetStatus.READY)
                .objectKey("media/chat/4/61.webp")
                .createdAt(LocalDateTime.now().minusDays(2))
                .uploadExpiresAt(LocalDateTime.now().minusDays(2))
                .build();

        when(storageConfig.getMediaOrphanRetentionHours()).thenReturn(24);
        when(storageConfig.getMediaCleanupBatchSize()).thenReturn(100);
        when(storageConfig.getCheerBucket()).thenReturn("chat-bucket");
        when(mediaAssetRepository.findUnlinkedAssetsOlderThan(
                eq(MediaAssetStatus.READY),
                any(LocalDateTime.class),
                eq(PageRequest.of(0, 100))))
                .thenReturn(List.of(asset));
        List<MediaAssetStatus> savedStatuses = new java.util.ArrayList<>();
        when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(invocation -> {
            MediaAsset savedAsset = invocation.getArgument(0);
            savedStatuses.add(savedAsset.getStatus());
            return savedAsset;
        });
        when(storageStrategy.deleteChecked("chat-bucket", asset.getObjectKey())).thenReturn(Mono.empty());

        MediaCleanupTargetReport report = mediaUploadService.cleanupUnlinkedReadyAssets();

        assertEquals(MediaAssetStatus.DELETED, asset.getStatus());
        assertEquals(MediaCleanupTarget.ORPHAN, report.target());
        assertEquals(1, report.scannedCount());
        assertEquals(1, report.deletedCount());
        assertEquals(0, report.errorCount());
        verify(mediaAssetRepository, times(2)).save(any(MediaAsset.class));
        assertEquals(List.of(MediaAssetStatus.ORPHANED, MediaAssetStatus.DELETED), savedStatuses);
        verify(storageStrategy).deleteChecked("chat-bucket", asset.getObjectKey());
        verify(metricsService).recordMediaCleanup("orphan", "deleted");
        verify(mediaAssetRepository).findUnlinkedAssetsOlderThan(
                eq(MediaAssetStatus.READY),
                any(LocalDateTime.class),
                eq(PageRequest.of(0, 100)));
    }

    @Test
    @DisplayName("media cleanup batch size는 최소 1 이상으로 repository PageRequest에 반영된다")
    void cleanupExpiredPendingAssets_usesMinimumBatchSize() {
        when(storageConfig.getMediaPendingRetentionHours()).thenReturn(24);
        when(storageConfig.getMediaCleanupBatchSize()).thenReturn(0);
        when(mediaAssetRepository.findByStatusAndUploadExpiresAtBeforeOrderByUploadExpiresAtAscIdAsc(
                eq(MediaAssetStatus.PENDING),
                any(LocalDateTime.class),
                any(Pageable.class)))
                .thenReturn(List.of());

        mediaUploadService.cleanupExpiredPendingAssets();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(mediaAssetRepository).findByStatusAndUploadExpiresAtBeforeOrderByUploadExpiresAtAscIdAsc(
                eq(MediaAssetStatus.PENDING),
                any(LocalDateTime.class),
                pageableCaptor.capture());
        assertEquals(PageRequest.of(0, 1), pageableCaptor.getValue());
    }

    private MediaAsset pendingDiaryAsset(long id) {
        return MediaAsset.builder()
                .id(id)
                .ownerUserId(9L)
                .domain(MediaDomain.DIARY)
                .status(MediaAssetStatus.PENDING)
                .originalFileName("diary.png")
                .declaredContentType("image/png")
                .declaredBytes(4L)
                .declaredWidth(1200)
                .declaredHeight(900)
                .stagingObjectKey("media/staging/diary/9/" + id + "-diary.png")
                .uploadExpiresAt(LocalDateTime.now().plusHours(1))
                .build();
    }

    private static StorageOperationException storageFailure(StorageOperationException.Kind kind) {
        return new StorageOperationException(kind, "storage " + kind, new RuntimeException("boom"));
    }

    @Test
    @DisplayName("staging object가 실제로 없으면(404) finalize는 NotFound로 실패하고 asset을 정리한다")
    void finalizeUpload_realObjectNotFoundCleansUp() {
        MediaAsset asset = pendingDiaryAsset(71L);
        when(mediaAssetRepository.findByIdAndOwnerUserId(71L, 9L)).thenReturn(Optional.of(asset));
        when(mediaAssetRepository.findByDerivedFrom_Id(71L)).thenReturn(Optional.empty());
        when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageConfig.getDiaryBucket()).thenReturn("diary-bucket");
        when(storageStrategy.exists("diary-bucket", asset.getStagingObjectKey())).thenReturn(Mono.just(false));
        when(storageStrategy.deleteChecked("diary-bucket", asset.getStagingObjectKey())).thenReturn(Mono.empty());

        NotFoundBusinessException exception = assertThrows(
                NotFoundBusinessException.class, () -> mediaUploadService.finalizeUpload(9L, 71L));

        assertEquals("MEDIA_STAGING_OBJECT_NOT_FOUND", exception.getCode());
        assertEquals(MediaAssetStatus.DELETED, asset.getStatus());
    }

    @Test
    @DisplayName("OCI 5xx/timeout으로 존재 여부를 알 수 없으면 PENDING과 staging object를 유지한다")
    void finalizeUpload_transientExistsFailureKeepsPending() {
        MediaAsset asset = pendingDiaryAsset(72L);
        when(mediaAssetRepository.findByIdAndOwnerUserId(72L, 9L)).thenReturn(Optional.of(asset));
        when(storageConfig.getDiaryBucket()).thenReturn("diary-bucket");
        when(storageStrategy.exists("diary-bucket", asset.getStagingObjectKey()))
                .thenReturn(Mono.error(storageFailure(StorageOperationException.Kind.TRANSIENT)));

        ServiceUnavailableBusinessException exception = assertThrows(
                ServiceUnavailableBusinessException.class, () -> mediaUploadService.finalizeUpload(9L, 72L));

        assertEquals("MEDIA_STORAGE_TEMPORARILY_UNAVAILABLE", exception.getCode());
        assertEquals(MediaAssetStatus.PENDING, asset.getStatus());
        verify(storageStrategy, never()).deleteChecked(any(), any());
        verify(mediaAssetRepository, never()).save(any(MediaAsset.class));
        verify(metricsService).recordMediaFinalize("DIARY", "storage_unavailable");
        verify(metricsService, never()).recordMediaFinalize("DIARY", "failure");
    }

    @Test
    @DisplayName("head 실패(일시 장애) 뒤 객체가 실제로 존재해도 asset은 삭제 상태로 바뀌지 않는다")
    void finalizeUpload_transientHeadFailureKeepsPending() {
        MediaAsset asset = pendingDiaryAsset(73L);
        when(mediaAssetRepository.findByIdAndOwnerUserId(73L, 9L)).thenReturn(Optional.of(asset));
        when(storageConfig.getDiaryBucket()).thenReturn("diary-bucket");
        when(storageStrategy.exists("diary-bucket", asset.getStagingObjectKey())).thenReturn(Mono.just(true));
        when(storageStrategy.head("diary-bucket", asset.getStagingObjectKey()))
                .thenReturn(Mono.error(storageFailure(StorageOperationException.Kind.TRANSIENT)));

        assertThrows(ServiceUnavailableBusinessException.class, () -> mediaUploadService.finalizeUpload(9L, 73L));

        assertEquals(MediaAssetStatus.PENDING, asset.getStatus());
        verify(storageStrategy, never()).deleteChecked(any(), any());
        verify(mediaAssetRepository, never()).save(any(MediaAsset.class));
    }

    @Test
    @DisplayName("download 중 connection timeout이어도 PENDING을 유지한다")
    void finalizeUpload_transientDownloadFailureKeepsPending() {
        MediaAsset asset = pendingDiaryAsset(74L);
        StoredObjectMetadata metadata = new StoredObjectMetadata(4L, "image/png");
        when(mediaAssetRepository.findByIdAndOwnerUserId(74L, 9L)).thenReturn(Optional.of(asset));
        when(storageConfig.getDiaryBucket()).thenReturn("diary-bucket");
        when(storageStrategy.exists("diary-bucket", asset.getStagingObjectKey())).thenReturn(Mono.just(true));
        when(storageStrategy.head("diary-bucket", asset.getStagingObjectKey())).thenReturn(Mono.just(metadata));
        when(storageStrategy.download("diary-bucket", asset.getStagingObjectKey()))
                .thenReturn(Mono.error(storageFailure(StorageOperationException.Kind.TRANSIENT)));

        assertThrows(ServiceUnavailableBusinessException.class, () -> mediaUploadService.finalizeUpload(9L, 74L));

        assertEquals(MediaAssetStatus.PENDING, asset.getStatus());
        verify(storageStrategy, never()).deleteChecked(any(), any());
    }

    @Test
    @DisplayName("영구적 스토리지 실패는 500으로 알리되 asset을 삭제 상태로 바꾸지 않는다")
    void finalizeUpload_permanentStorageFailureDoesNotDeleteAsset() {
        MediaAsset asset = pendingDiaryAsset(75L);
        when(mediaAssetRepository.findByIdAndOwnerUserId(75L, 9L)).thenReturn(Optional.of(asset));
        when(storageConfig.getDiaryBucket()).thenReturn("diary-bucket");
        when(storageStrategy.exists("diary-bucket", asset.getStagingObjectKey()))
                .thenReturn(Mono.error(storageFailure(StorageOperationException.Kind.PERMANENT)));

        com.example.common.exception.InternalServerBusinessException exception = assertThrows(
                com.example.common.exception.InternalServerBusinessException.class,
                () -> mediaUploadService.finalizeUpload(9L, 75L));

        assertEquals("MEDIA_STORAGE_FAILURE", exception.getCode());
        assertEquals(MediaAssetStatus.PENDING, asset.getStatus());
        verify(storageStrategy, never()).deleteChecked(any(), any());
    }

    @Test
    @DisplayName("검증 실패 뒤 staging 삭제 API가 실패하면 asset을 DELETED로 기록하지 않는다")
    void finalizeUpload_cleanupDeleteFailureDoesNotMarkDeleted() {
        MediaAsset asset = pendingDiaryAsset(76L);
        StoredObjectMetadata metadata = new StoredObjectMetadata(4L, "image/png");
        when(mediaAssetRepository.findByIdAndOwnerUserId(76L, 9L)).thenReturn(Optional.of(asset));
        when(storageConfig.getDiaryBucket()).thenReturn("diary-bucket");
        when(storageStrategy.exists("diary-bucket", asset.getStagingObjectKey())).thenReturn(Mono.just(true));
        when(storageStrategy.head("diary-bucket", asset.getStagingObjectKey())).thenReturn(Mono.just(metadata));
        doThrow(new BadRequestBusinessException("INVALID_MEDIA_FILE_SIZE", "too large"))
                .when(validationService).validateStoredObjectMetadata(asset, metadata);
        when(storageStrategy.deleteChecked("diary-bucket", asset.getStagingObjectKey()))
                .thenReturn(Mono.error(storageFailure(StorageOperationException.Kind.TRANSIENT)));

        assertThrows(BadRequestBusinessException.class, () -> mediaUploadService.finalizeUpload(9L, 76L));

        assertEquals(MediaAssetStatus.PENDING, asset.getStatus());
        verify(mediaAssetRepository, never()).save(any(MediaAsset.class));
    }

    @Test
    @DisplayName("사용자 delete에서 스토리지 삭제가 실패하면 503이고 asset은 삭제 처리되지 않는다")
    void deleteUpload_storageFailureDoesNotMarkDeleted() {
        MediaAsset asset = pendingDiaryAsset(77L);
        when(mediaAssetRepository.findByIdAndOwnerUserId(77L, 9L)).thenReturn(Optional.of(asset));
        when(mediaAssetLinkRepository.existsByAssetId(77L)).thenReturn(false);
        when(storageConfig.getDiaryBucket()).thenReturn("diary-bucket");
        when(storageStrategy.deleteChecked("diary-bucket", asset.getStagingObjectKey()))
                .thenReturn(Mono.error(storageFailure(StorageOperationException.Kind.TRANSIENT)));

        assertThrows(ServiceUnavailableBusinessException.class, () -> mediaUploadService.deleteUpload(9L, 77L));

        assertEquals(MediaAssetStatus.PENDING, asset.getStatus());
        verify(mediaAssetRepository, never()).save(any(MediaAsset.class));
    }

    @Test
    @DisplayName("만료 pending cleanup에서 삭제가 실패하면 error로 집계하고 DELETED로 바꾸지 않는다")
    void cleanupExpiredPendingAssets_deleteFailureCountsErrorAndKeepsPending() {
        MediaAsset asset = pendingDiaryAsset(78L);
        when(storageConfig.getMediaPendingRetentionHours()).thenReturn(24);
        when(storageConfig.getMediaCleanupBatchSize()).thenReturn(100);
        when(storageConfig.getDiaryBucket()).thenReturn("diary-bucket");
        when(mediaAssetRepository.findByStatusAndUploadExpiresAtBeforeOrderByUploadExpiresAtAscIdAsc(
                eq(MediaAssetStatus.PENDING), any(LocalDateTime.class), eq(PageRequest.of(0, 100))))
                .thenReturn(List.of(asset));
        when(storageStrategy.deleteChecked("diary-bucket", asset.getStagingObjectKey()))
                .thenReturn(Mono.error(storageFailure(StorageOperationException.Kind.TRANSIENT)));

        MediaCleanupTargetReport report = mediaUploadService.cleanupExpiredPendingAssets();

        assertEquals(MediaAssetStatus.PENDING, asset.getStatus());
        assertEquals(0, report.deletedCount());
        assertEquals(1, report.errorCount());
        verify(metricsService).recordMediaCleanup("pending", "error");
    }

    @Test
    @DisplayName("orphan cleanup에서 삭제가 실패하면 ORPHANED가 아니라 READY로 되돌려 다음 주기에 재시도한다")
    void cleanupUnlinkedReadyAssets_deleteFailureRestoresReady() {
        MediaAsset asset = MediaAsset.builder()
                .id(79L)
                .ownerUserId(4L)
                .domain(MediaDomain.CHAT)
                .status(MediaAssetStatus.READY)
                .objectKey("media/chat/4/79.webp")
                .createdAt(LocalDateTime.now().minusDays(2))
                .uploadExpiresAt(LocalDateTime.now().minusDays(2))
                .build();
        when(storageConfig.getMediaOrphanRetentionHours()).thenReturn(24);
        when(storageConfig.getMediaCleanupBatchSize()).thenReturn(100);
        when(storageConfig.getCheerBucket()).thenReturn("chat-bucket");
        when(mediaAssetRepository.findUnlinkedAssetsOlderThan(
                eq(MediaAssetStatus.READY), any(LocalDateTime.class), eq(PageRequest.of(0, 100))))
                .thenReturn(List.of(asset));
        when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageStrategy.deleteChecked("chat-bucket", asset.getObjectKey()))
                .thenReturn(Mono.error(storageFailure(StorageOperationException.Kind.TRANSIENT)));

        MediaCleanupTargetReport report = mediaUploadService.cleanupUnlinkedReadyAssets();

        assertEquals(MediaAssetStatus.READY, asset.getStatus());
        assertEquals(1, report.errorCount());
        assertEquals(0, report.deletedCount());
    }

    @Test
    @DisplayName("최종 객체 업로드 중 일시 장애가 나도 staging과 PENDING을 유지하고 방금 쓴 객체만 치운다")
    void finalizeUpload_transientUploadFailureKeepsStagingAndPending() throws Exception {
        byte[] originalBytes = new byte[] {1, 2, 3, 4};
        byte[] optimizedBytes = new byte[] {5, 6};
        MediaAsset asset = pendingDiaryAsset(80L);
        StoredObjectMetadata metadata = new StoredObjectMetadata(4L, "image/png");
        when(mediaAssetRepository.findByIdAndOwnerUserId(80L, 9L)).thenReturn(Optional.of(asset));
        when(storageConfig.getDiaryBucket()).thenReturn("diary-bucket");
        when(storageStrategy.exists("diary-bucket", asset.getStagingObjectKey())).thenReturn(Mono.just(true));
        when(storageStrategy.head("diary-bucket", asset.getStagingObjectKey())).thenReturn(Mono.just(metadata));
        when(storageStrategy.download("diary-bucket", asset.getStagingObjectKey()))
                .thenReturn(Mono.just(new StoredObject(originalBytes, "image/png")));
        when(validationService.getActualDimension(originalBytes)).thenReturn(new ImageUtil.ImageDimension(1200, 900));
        when(validationService.getActualDimension(optimizedBytes)).thenReturn(new ImageUtil.ImageDimension(800, 600));
        when(imageUtil.process(any(), eq("media_diary")))
                .thenReturn(new ImageUtil.ProcessedImage(optimizedBytes, "image/webp", "webp"));
        when(storageStrategy.uploadBytes(any(), any(), eq("diary-bucket"), any()))
                .thenReturn(Mono.error(storageFailure(StorageOperationException.Kind.TRANSIENT)));
        when(storageStrategy.deleteChecked("diary-bucket", "media/diary/9/80.webp")).thenReturn(Mono.empty());

        assertThrows(ServiceUnavailableBusinessException.class, () -> mediaUploadService.finalizeUpload(9L, 80L));

        assertEquals(MediaAssetStatus.PENDING, asset.getStatus());
        verify(storageStrategy, never()).deleteChecked("diary-bucket", asset.getStagingObjectKey());
        verify(mediaAssetRepository, never()).save(any(MediaAsset.class));
        verify(metricsService).recordMediaFinalize("DIARY", "storage_unavailable");
    }
}
