package com.example.media.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.strategy.StorageStrategy;
import com.example.cheerboard.storage.strategy.StoredObject;
import com.example.common.exception.BadRequestBusinessException;
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

        when(mediaAssetRepository.findById(11L)).thenReturn(Optional.of(asset));
        when(mediaAssetRepository.findByDerivedFrom_Id(11L)).thenReturn(Optional.empty());
        when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageConfig.getProfileBucket()).thenReturn("profile-bucket");
        when(storageConfig.getSignedUrlTtlSeconds()).thenReturn(600);
        when(storageStrategy.exists("profile-bucket", asset.getStagingObjectKey())).thenReturn(Mono.just(true));
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
        when(storageStrategy.delete("profile-bucket", asset.getStagingObjectKey())).thenReturn(Mono.empty());
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

        when(mediaAssetRepository.findById(21L)).thenReturn(Optional.of(asset));
        when(mediaAssetRepository.findByDerivedFrom_Id(21L)).thenReturn(Optional.empty());
        when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageConfig.getDiaryBucket()).thenReturn("diary-bucket");
        when(storageStrategy.exists("diary-bucket", asset.getStagingObjectKey())).thenReturn(Mono.just(true));
        when(storageStrategy.download("diary-bucket", asset.getStagingObjectKey()))
                .thenReturn(Mono.just(new StoredObject(originalBytes, "image/png")));
        when(validationService.getActualDimension(originalBytes)).thenReturn(new ImageUtil.ImageDimension(1200, 900));
        when(storageStrategy.delete("diary-bucket", asset.getStagingObjectKey())).thenReturn(Mono.empty());
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
        when(storageConfig.getCheerBucket()).thenReturn("cheer-bucket");
        when(mediaAssetRepository.findByStatusAndUploadExpiresAtBefore(eq(MediaAssetStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of(asset));
        when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(storageStrategy.delete("cheer-bucket", asset.getStagingObjectKey())).thenReturn(Mono.empty());

        MediaCleanupTargetReport report = mediaUploadService.cleanupExpiredPendingAssets();

        assertEquals(MediaAssetStatus.DELETED, asset.getStatus());
        assertEquals(MediaCleanupTarget.PENDING, report.target());
        assertEquals(1, report.scannedCount());
        assertEquals(1, report.deletedCount());
        assertEquals(0, report.errorCount());
        verify(storageStrategy).delete("cheer-bucket", asset.getStagingObjectKey());
        verify(metricsService).recordMediaCleanup("pending", "deleted");
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
        when(storageConfig.getCheerBucket()).thenReturn("chat-bucket");
        when(mediaAssetRepository.findUnlinkedAssetsOlderThan(eq(MediaAssetStatus.READY), any(LocalDateTime.class)))
                .thenReturn(List.of(asset));
        List<MediaAssetStatus> savedStatuses = new java.util.ArrayList<>();
        when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(invocation -> {
            MediaAsset savedAsset = invocation.getArgument(0);
            savedStatuses.add(savedAsset.getStatus());
            return savedAsset;
        });
        when(storageStrategy.delete("chat-bucket", asset.getObjectKey())).thenReturn(Mono.empty());

        MediaCleanupTargetReport report = mediaUploadService.cleanupUnlinkedReadyAssets();

        assertEquals(MediaAssetStatus.DELETED, asset.getStatus());
        assertEquals(MediaCleanupTarget.ORPHAN, report.target());
        assertEquals(1, report.scannedCount());
        assertEquals(1, report.deletedCount());
        assertEquals(0, report.errorCount());
        verify(mediaAssetRepository, times(2)).save(any(MediaAsset.class));
        assertEquals(List.of(MediaAssetStatus.ORPHANED, MediaAssetStatus.DELETED), savedStatuses);
        verify(storageStrategy).delete("chat-bucket", asset.getObjectKey());
        verify(metricsService).recordMediaCleanup("orphan", "deleted");
    }
}
