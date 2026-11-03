package com.example.media.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.strategy.StorageOperationException;
import com.example.cheerboard.storage.strategy.PresignedUpload;
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
import com.example.media.dto.InitMediaUploadRequest;
import com.example.media.entity.MediaAsset;
import com.example.media.entity.MediaAssetStatus;
import com.example.media.entity.MediaCleanupTarget;
import com.example.media.entity.MediaDomain;
import com.example.media.repository.MediaAssetLinkRepository;
import com.example.media.repository.MediaAssetRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MediaUploadServiceTest {

    @InjectMocks
    private MediaUploadService mediaUploadService;

    @Mock
    private MediaAssetRepository mediaAssetRepository;

    @Mock
    private MediaFinalizeClaimService finalizeClaimService;

    @Mock
    private MediaFinalizeAttemptCleanupService finalizeAttemptCleanupService;

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

    @BeforeEach
    void setUpFinalizeClaimDefaults() {
        when(finalizeAttemptCleanupService.cleanupForAsset(anyLong())).thenReturn(true);
        when(finalizeClaimService.claim(anyLong(), anyLong(), anyString())).thenAnswer(invocation -> {
            Long userId = invocation.getArgument(0);
            Long assetId = invocation.getArgument(1);
            return mediaAssetRepository.findByIdAndOwnerUserId(assetId, userId)
                    .filter(asset -> asset.getStatus() == MediaAssetStatus.PENDING
                            || asset.getStatus() == MediaAssetStatus.FINALIZING)
                    .map(asset -> {
                        String token = testToken(assetId);
                        asset.setStatus(MediaAssetStatus.FINALIZING);
                        asset.setFinalizeToken(token);
                        asset.setFinalizeLeaseUntil(LocalDateTime.now().plusMinutes(15));
                        return new MediaFinalizeClaimService.FinalizeClaim(asset, token);
                    });
        });
        when(finalizeClaimService.renew(any())).thenAnswer(invocation -> {
            MediaFinalizeClaimService.FinalizeClaim claim = invocation.getArgument(0);
            return claim.asset().getStatus() == MediaAssetStatus.FINALIZING
                    && claim.token().equals(claim.asset().getFinalizeToken());
        });
        when(finalizeClaimService.recordObjectKeys(any(), anyString(), nullable(String.class)))
                .thenAnswer(invocation -> {
                    MediaFinalizeClaimService.FinalizeClaim claim = invocation.getArgument(0);
                    if (claim.asset().getStatus() != MediaAssetStatus.FINALIZING
                            || !claim.token().equals(claim.asset().getFinalizeToken())) {
                        return false;
                    }
                    claim.asset().setFinalizeObjectKey(invocation.getArgument(1));
                    claim.asset().setFinalizeFeedObjectKey(invocation.getArgument(2));
                    return true;
                });
        when(finalizeClaimService.clearObjectKeys(any())).thenAnswer(invocation -> {
            MediaFinalizeClaimService.FinalizeClaim claim = invocation.getArgument(0);
            if (claim.asset().getStatus() != MediaAssetStatus.FINALIZING
                    || !claim.token().equals(claim.asset().getFinalizeToken())) {
                return false;
            }
            claim.asset().setFinalizeObjectKey(null);
            claim.asset().setFinalizeFeedObjectKey(null);
            return true;
        });
        doAnswer(invocation -> {
            MediaFinalizeClaimService.FinalizeClaim claim = invocation.getArgument(0);
            if (claim.asset().getStatus() == MediaAssetStatus.FINALIZING
                    && claim.token().equals(claim.asset().getFinalizeToken())) {
                claim.asset().setStatus(MediaAssetStatus.PENDING);
                claim.asset().setFinalizeToken(null);
                claim.asset().setFinalizeLeaseUntil(null);
            }
            return null;
        }).when(finalizeClaimService).release(any());
        when(finalizeClaimService.complete(any(), any(), nullable(MediaFinalizeClaimService.FinalizedObject.class)))
                .thenAnswer(invocation -> {
                    MediaFinalizeClaimService.FinalizeClaim claim = invocation.getArgument(0);
                    MediaFinalizeClaimService.FinalizedObject primary = invocation.getArgument(1);
                    claim.asset().markReady(
                            primary.objectKey(), primary.contentType(), primary.bytes(), primary.width(), primary.height());
                    claim.asset().setFinalizeToken(null);
                    claim.asset().setFinalizeLeaseUntil(null);
                    claim.asset().setFinalizeObjectKey(null);
                    claim.asset().setFinalizeFeedObjectKey(null);
                    return Optional.of(claim.asset());
                });
        when(finalizeClaimService.claimExpiredPendingForCleanup(any(), any(), any())).thenAnswer(invocation -> {
            MediaAsset asset = invocation.getArgument(0);
            String token = testToken(asset.getId());
            asset.setStatus(MediaAssetStatus.FINALIZING);
            asset.setFinalizeToken(token);
            return Optional.of(new MediaFinalizeClaimService.FinalizeCleanupClaim(asset, token));
        });
        when(finalizeClaimService.claimExpiredForCleanup(any(), any(), any())).thenAnswer(invocation -> {
            MediaAsset asset = invocation.getArgument(0);
            String token = testToken(asset.getId());
            asset.setFinalizeToken(token);
            return Optional.of(new MediaFinalizeClaimService.FinalizeCleanupClaim(asset, token));
        });
        when(finalizeClaimService.markExpiredCleanupComplete(any())).thenAnswer(invocation -> {
            MediaFinalizeClaimService.FinalizeCleanupClaim claim = invocation.getArgument(0);
            claim.asset().setStatus(MediaAssetStatus.DELETED);
            claim.asset().setFinalizeLeaseUntil(LocalDateTime.now().plusMinutes(15));
            claim.asset().setFinalizeCleanupVerifiedAt(null);
            return true;
        });
        when(finalizeClaimService.claimDeletedTombstoneForCleanup(any(), any())).thenAnswer(invocation -> {
            MediaAsset asset = invocation.getArgument(0);
            String token = testToken(asset.getId());
            asset.setFinalizeToken(token);
            return Optional.of(new MediaFinalizeClaimService.FinalizeCleanupClaim(asset, token));
        });
        when(finalizeClaimService.claimOwnedAssetForAccountDeletion(any(MediaAsset.class), anyLong()))
                .thenAnswer(invocation -> {
                    MediaAsset asset = invocation.getArgument(0);
                    String token = testToken(asset.getId() + 100_000L);
                    asset.setStatus(MediaAssetStatus.DELETED);
                    asset.setOwnerUserId(null);
                    asset.setOriginalFileName(null);
                    asset.setDeclaredContentType("application/octet-stream");
                    asset.setDeclaredBytes(0L);
                    asset.setDeclaredWidth(0);
                    asset.setDeclaredHeight(0);
                    asset.setStoredContentType(null);
                    asset.setStoredBytes(null);
                    asset.setStoredWidth(null);
                    asset.setStoredHeight(null);
                    asset.setDerivedFrom(null);
                    asset.setFinalizeToken(token);
                    asset.setFinalizeLeaseUntil(LocalDateTime.now().plusHours(2));
                    asset.setFinalizeCleanupVerifiedAt(null);
                    return Optional.of(new MediaFinalizeClaimService.FinalizeCleanupClaim(asset, token));
                });
        when(finalizeClaimService.claimOwnedAssetForDeletion(any(MediaAsset.class), anyLong()))
                .thenAnswer(invocation -> {
                    MediaAsset asset = invocation.getArgument(0);
                    String token = testToken(asset.getId() + 100_000L);
                    asset.setStatus(MediaAssetStatus.DELETED);
                    asset.setFinalizeToken(token);
                    asset.setFinalizeLeaseUntil(LocalDateTime.now().plusHours(2));
                    asset.setFinalizeCleanupVerifiedAt(null);
                    return Optional.of(new MediaFinalizeClaimService.FinalizeCleanupClaim(asset, token));
                });
        when(finalizeClaimService.updateDeletedTombstoneVerification(any(), nullable(LocalDateTime.class)))
                .thenAnswer(invocation -> {
                    MediaFinalizeClaimService.FinalizeCleanupClaim claim = invocation.getArgument(0);
                    claim.asset().setFinalizeCleanupVerifiedAt(invocation.getArgument(1));
                    claim.asset().setFinalizeLeaseUntil(LocalDateTime.now().plusHours(2));
                    return true;
                });
        when(finalizeClaimService.clearDeletedTombstone(any())).thenAnswer(invocation -> {
            MediaFinalizeClaimService.FinalizeCleanupClaim claim = invocation.getArgument(0);
            claim.asset().setFinalizeToken(null);
            claim.asset().setFinalizeLeaseUntil(null);
            claim.asset().setFinalizeObjectKey(null);
            claim.asset().setFinalizeFeedObjectKey(null);
            claim.asset().setFinalizeCleanupVerifiedAt(null);
            claim.asset().setStagingObjectKey(MediaAsset.CLEARED_STAGING_OBJECT_KEY);
            claim.asset().setObjectKey(null);
            return true;
        });
    }

    @ParameterizedTest
    @EnumSource(MediaDomain.class)
    @DisplayName("모든 업로드 도메인은 quota에 사용한 선언 크기를 presigned PUT 서명에도 고정한다")
    void initUploadBindsDeclaredSizeForEveryDomain(MediaDomain domain) {
        long declaredBytes = 2_048L;
        InitMediaUploadRequest request = new InitMediaUploadRequest(
                domain, "seat.png", "image/png", declaredBytes, 100, 100);
        MediaAsset persisted = MediaAsset.builder()
                .id(81L)
                .ownerUserId(7L)
                .domain(domain)
                .status(MediaAssetStatus.PENDING)
                .originalFileName("seat.png")
                .declaredContentType("image/png")
                .declaredBytes(declaredBytes)
                .declaredWidth(100)
                .declaredHeight(100)
                .stagingObjectKey("pending")
                .uploadExpiresAt(LocalDateTime.now().plusMinutes(15))
                .build();

        switch (domain) {
            case PROFILE -> when(storageConfig.getProfileBucket()).thenReturn("bucket");
            case DIARY, SEAT_VIEW -> when(storageConfig.getDiaryBucket()).thenReturn("bucket");
            case CHEER, CHAT -> when(storageConfig.getCheerBucket()).thenReturn("bucket");
        }
        when(storageConfig.getMediaUploadUrlTtlSeconds()).thenReturn(900);
        when(mediaAssetRepository.saveAndFlush(any(MediaAsset.class))).thenReturn(persisted);
        when(storageStrategy.presignPut(
                eq("bucket"), any(String.class), eq("image/png"), eq(declaredBytes), eq(900)))
                .thenReturn(Mono.just(new PresignedUpload("https://storage.example/put", java.util.Map.of(
                        "Content-Type", "image/png"))));

        var response = mediaUploadService.initUpload(7L, request);

        assertEquals(81L, response.assetId());
        assertEquals("https://storage.example/put", response.uploadUrl());
        verify(mediaQuotaService).assertWithinQuota(7L, domain, declaredBytes);
        verify(storageStrategy).presignPut(
                eq("bucket"), any(String.class), eq("image/png"), eq(declaredBytes), eq(900));
    }

    @Test
    @DisplayName("승인된 시야뷰 기록을 위해 계정 삭제 때 SEAT_VIEW 원본과 링크를 보존한다")
    void deleteOwnedAssetsForAccount_retainsSeatViewAsset() {
        MediaAsset seatViewAsset = MediaAsset.builder()
                .id(91L)
                .ownerUserId(7L)
                .domain(MediaDomain.SEAT_VIEW)
                .status(MediaAssetStatus.READY)
                .originalFileName("seat-view.jpg")
                .declaredContentType("image/jpeg")
                .declaredBytes(64L)
                .declaredWidth(320)
                .declaredHeight(240)
                .stagingObjectKey("media/staging/seat-view/7/91-seat-view.jpg")
                .objectKey("media/seat-view/7/91-seat-view.jpg")
                .uploadExpiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(mediaAssetRepository.findByOwnerUserIdAndIdGreaterThanOrderByIdAsc(
                eq(7L), eq(0L), any(Pageable.class)))
                .thenReturn(List.of(seatViewAsset));

        mediaUploadService.deleteOwnedAssetsForAccount(7L, Set.of());

        assertEquals(7L, seatViewAsset.getOwnerUserId());
        assertEquals(MediaAssetStatus.READY, seatViewAsset.getStatus());
        assertEquals("media/seat-view/7/91-seat-view.jpg", seatViewAsset.getObjectKey());
        verify(mediaAssetRepository, never()).save(any(MediaAsset.class));
        verify(mediaAssetLinkRepository, never()).deleteByAssetId(91L);
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
    }

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
        String stagingKey = asset.getStagingObjectKey();
        when(storageStrategy.exists("profile-bucket", stagingKey))
                .thenReturn(Mono.just(true), Mono.just(false));
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
        when(storageStrategy.getUrl(eq("profile-bucket"), anyString(), eq(600)))
                .thenAnswer(invocation -> Mono.just("https://signed.example/" + invocation.getArgument(1)));

        FinalizeMediaUploadResponse response = mediaUploadService.finalizeUpload(7L, 11L);

        String primaryKey = MediaDomain.PROFILE.buildFinalObjectKey(7L, 11L, testToken(11L), "webp");
        String feedKey = MediaDomain.PROFILE.buildProfileFeedObjectKey(7L, 11L, testToken(11L), "webp");
        assertEquals(primaryKey, response.storagePath());
        assertEquals("https://signed.example/" + primaryKey, response.publicUrl());
        assertEquals(MediaAssetStatus.READY, asset.getStatus());
        assertEquals(primaryKey, asset.getObjectKey());
        verify(finalizeClaimService).complete(
                any(),
                eq(new MediaFinalizeClaimService.FinalizedObject(primaryKey, "image/webp", 2L, 512, 512)),
                eq(new MediaFinalizeClaimService.FinalizedObject(feedKey, "image/webp", 1L, 160, 160)));
        verify(storageStrategy).deleteChecked("profile-bucket", stagingKey);
        assertEquals(stagingKey, asset.getStagingObjectKey(),
                "Keep tracking the staging key until the presigned PUT is expired and quiescent.");
        verify(metricsService).recordMediaFinalize("PROFILE", "success");
    }

    @Test
    @DisplayName("media finalize 검증 실패는 공유 staging을 보존하고 claim을 해제한다")
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
        assertEquals(MediaAssetStatus.PENDING, asset.getStatus());
        verify(storageStrategy, never()).deleteChecked(any(), eq(asset.getStagingObjectKey()));
        verify(finalizeClaimService).release(any());
        verify(metricsService).recordMediaFinalize("DIARY", "failure");
    }

    @Test
    @DisplayName("시야뷰 finalize 재호출은 인증된 미리보기 경로를 위해 bearer URL을 발급하지 않는다")
    void finalizeUpload_seatViewDoesNotIssuePublicUrl() {
        MediaAsset asset = MediaAsset.builder()
                .id(93L)
                .ownerUserId(7L)
                .domain(MediaDomain.SEAT_VIEW)
                .status(MediaAssetStatus.READY)
                .objectKey("media/seat-view/7/93.webp")
                .stagingObjectKey("media/staging/seat-view/7/93-seat.webp")
                .build();
        when(mediaAssetRepository.findByIdAndOwnerUserId(93L, 7L)).thenReturn(Optional.of(asset));

        FinalizeMediaUploadResponse response = mediaUploadService.finalizeUpload(7L, 93L);

        assertEquals("media/seat-view/7/93.webp", response.storagePath());
        assertNull(response.publicUrl());
        verify(storageStrategy, never()).getUrl(any(), any(), anyInt());
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
        assertEquals(MediaAssetStatus.PENDING, asset.getStatus());
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
    @DisplayName("이미 다른 finalize가 claim한 asset은 storage I/O를 시작하지 않는다")
    void finalizeUpload_rejectsConcurrentClaimWithoutStorageIo() {
        MediaAsset asset = pendingDiaryAsset(32L);
        when(mediaAssetRepository.findByIdAndOwnerUserId(32L, 9L)).thenReturn(Optional.of(asset));
        doReturn(Optional.empty()).when(finalizeClaimService).claim(eq(9L), eq(32L), nullable(String.class));

        ServiceUnavailableBusinessException exception = assertThrows(
                ServiceUnavailableBusinessException.class,
                () -> mediaUploadService.finalizeUpload(9L, 32L));

        assertEquals("MEDIA_FINALIZATION_IN_PROGRESS", exception.getCode());
        verify(storageStrategy, never()).exists(any(), any());
        verify(storageStrategy, never()).uploadBytes(any(), any(), any(), any());
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
        when(storageStrategy.exists("cheer-bucket", asset.getStagingObjectKey())).thenReturn(Mono.just(false));

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
    @DisplayName("만료된 FINALIZING asset cleanup은 fencing claim 후 staging과 시도 객체를 함께 정리한다")
    void cleanupExpiredPendingAssets_deletesExpiredFinalizeAttempt() {
        MediaAsset asset = pendingDiaryAsset(52L);
        asset.setStatus(MediaAssetStatus.FINALIZING);
        asset.setFinalizeToken(testToken(52L));
        asset.setFinalizeObjectKey("media/diary/9/52/attempt.webp");
        asset.setFinalizeFeedObjectKey("media/profile-feed/9/52/attempt.webp");
        when(storageConfig.getMediaPendingRetentionHours()).thenReturn(24);
        when(storageConfig.getMediaCleanupBatchSize()).thenReturn(100);
        when(storageConfig.getDiaryBucket()).thenReturn("diary-bucket");
        when(mediaAssetRepository.findByStatusAndUploadExpiresAtBeforeOrderByUploadExpiresAtAscIdAsc(
                eq(MediaAssetStatus.PENDING), any(LocalDateTime.class), eq(PageRequest.of(0, 100))))
                .thenReturn(List.of());
        when(mediaAssetRepository
                .findByStatusAndFinalizeLeaseUntilBeforeAndUploadExpiresAtBeforeOrderByFinalizeLeaseUntilAscUploadExpiresAtAscIdAsc(
                        eq(MediaAssetStatus.FINALIZING), any(LocalDateTime.class), any(LocalDateTime.class),
                        eq(PageRequest.of(0, 100))))
                .thenReturn(List.of(asset));
        when(storageStrategy.deleteChecked("diary-bucket", asset.getStagingObjectKey())).thenReturn(Mono.empty());
        when(storageStrategy.deleteChecked("diary-bucket", asset.getFinalizeObjectKey())).thenReturn(Mono.empty());
        when(storageStrategy.deleteChecked("diary-bucket", asset.getFinalizeFeedObjectKey())).thenReturn(Mono.empty());
        when(storageStrategy.exists("diary-bucket", asset.getStagingObjectKey())).thenReturn(Mono.just(false));
        when(storageStrategy.exists("diary-bucket", asset.getFinalizeObjectKey())).thenReturn(Mono.just(false));
        when(storageStrategy.exists("diary-bucket", asset.getFinalizeFeedObjectKey())).thenReturn(Mono.just(false));

        MediaCleanupTargetReport report = mediaUploadService.cleanupExpiredPendingAssets();

        assertEquals(1, report.scannedCount());
        assertEquals(1, report.deletedCount());
        assertEquals(MediaAssetStatus.DELETED, asset.getStatus());
        verify(storageStrategy).deleteChecked("diary-bucket", asset.getStagingObjectKey());
        verify(storageStrategy).deleteChecked("diary-bucket", "media/diary/9/52/attempt.webp");
        verify(storageStrategy).deleteChecked("diary-bucket", "media/profile-feed/9/52/attempt.webp");
    }

    @Test
    @DisplayName("READY staging 삭제 실패는 staging key를 보존해 다음 스케줄에서 재시도한다")
    void cleanupReadyStagingObjectsRetriesUntilAbsent() {
        MediaAsset asset = MediaAsset.builder()
                .id(53L)
                .ownerUserId(7L)
                .domain(MediaDomain.PROFILE)
                .status(MediaAssetStatus.READY)
                .stagingObjectKey("media/staging/profile/7/53-avatar.png")
                .objectKey("media/profile/7/53/attempt.webp")
                .build();
        when(storageConfig.getMediaCleanupBatchSize()).thenReturn(1);
        when(storageConfig.getProfileBucket()).thenReturn("profile-bucket");
        LocalDateTime now = LocalDateTime.of(2026, 10, 25, 12, 0);
        when(mediaAssetRepository
                .findByStatusAndStagingObjectKeyIsNotNullAndStagingObjectKeyNotAndUploadExpiresAtBeforeAndIdGreaterThanOrderByIdAsc(
                        eq(MediaAssetStatus.READY),
                        eq(MediaAsset.CLEARED_STAGING_OBJECT_KEY),
                        eq(now.minusMinutes(15)),
                        anyLong(),
                        eq(PageRequest.of(0, 1))))
                .thenReturn(List.of(asset), List.of(), List.of(asset), List.of());
        when(storageStrategy.deleteChecked("profile-bucket", asset.getStagingObjectKey()))
                .thenReturn(Mono.error(storageFailure(StorageOperationException.Kind.TRANSIENT)), Mono.empty());
        when(storageStrategy.exists("profile-bucket", asset.getStagingObjectKey())).thenReturn(Mono.just(false));
        when(mediaAssetRepository.clearReadyStagingObjectKey(
                eq(53L), eq(asset.getStagingObjectKey()), eq(MediaAsset.CLEARED_STAGING_OBJECT_KEY),
                eq(now), eq(now.minusMinutes(15)))).thenReturn(1);

        mediaUploadService.cleanupReadyStagingObjects(now);
        mediaUploadService.cleanupReadyStagingObjects(now);

        verify(storageStrategy, times(2)).deleteChecked("profile-bucket", asset.getStagingObjectKey());
        verify(mediaAssetRepository).clearReadyStagingObjectKey(
                eq(53L), eq(asset.getStagingObjectKey()), eq(MediaAsset.CLEARED_STAGING_OBJECT_KEY),
                eq(now), eq(now.minusMinutes(15)));
    }

    @Test
    @DisplayName("presigned PUT 만료 후 15분 유예가 지나기 전에는 READY staging 객체를 정리하지 않는다")
    void cleanupReadyStagingObjectsWaitsForUploadQuiescence() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 25, 12, 0);
        when(storageConfig.getMediaCleanupBatchSize()).thenReturn(10);
        when(mediaAssetRepository
                .findByStatusAndStagingObjectKeyIsNotNullAndStagingObjectKeyNotAndUploadExpiresAtBeforeAndIdGreaterThanOrderByIdAsc(
                        eq(MediaAssetStatus.READY),
                        eq(MediaAsset.CLEARED_STAGING_OBJECT_KEY),
                        eq(now.minusMinutes(15)),
                        anyLong(),
                        eq(PageRequest.of(0, 10))))
                .thenReturn(List.of());

        mediaUploadService.cleanupReadyStagingObjects(now);

        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
        verify(mediaAssetRepository, never()).clearReadyStagingObjectKey(
                anyLong(), anyString(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("계정 삭제 중 FINALIZING asset은 fencing key를 tombstone으로 남겨 늦은 PUT도 재정리한다")
    void deleteOwnedAssetsForAccountRetainsFinalizingCleanupTombstone() {
        String token = testToken(54L);
        MediaAsset asset = MediaAsset.builder()
                .id(54L)
                .ownerUserId(7L)
                .domain(MediaDomain.PROFILE)
                .status(MediaAssetStatus.FINALIZING)
                .originalFileName("avatar.png")
                .declaredContentType("image/png")
                .declaredBytes(10L)
                .declaredWidth(10)
                .declaredHeight(10)
                .stagingObjectKey("media/staging/profile/7/54-avatar.png")
                .finalizeToken(token)
                .finalizeLeaseUntil(LocalDateTime.now().plusMinutes(10))
                .finalizeObjectKey("media/profile/7/54/" + token + ".webp")
                .finalizeFeedObjectKey("media/profile-feed/7/54/" + token + ".webp")
                .uploadExpiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(mediaAssetRepository.findByOwnerUserIdAndIdGreaterThanOrderByIdAsc(
                eq(7L), eq(0L), any(Pageable.class))).thenReturn(List.of(asset));
        when(storageConfig.getProfileBucket()).thenReturn("profile-bucket");
        when(storageStrategy.deleteChecked(eq("profile-bucket"), anyString())).thenReturn(Mono.empty());
        when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mediaUploadService.deleteOwnedAssetsForAccount(7L, Set.of());

        assertEquals(MediaAssetStatus.DELETED, asset.getStatus());
        assertNull(asset.getOwnerUserId());
        assertNotEquals(token, asset.getFinalizeToken());
        assertEquals(testToken(100_054L), asset.getFinalizeToken());
        assertTrue(asset.getFinalizeLeaseUntil().isAfter(LocalDateTime.now()));
        assertTrue(asset.getFinalizeLeaseUntil().isAfter(asset.getUploadExpiresAt()));
        assertEquals("media/staging/profile/7/54-avatar.png", asset.getStagingObjectKey());
        assertEquals("media/profile/7/54/" + token + ".webp", asset.getFinalizeObjectKey());
        assertNull(asset.getOriginalFileName());
        assertEquals("application/octet-stream", asset.getDeclaredContentType());
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
    }

    @Test
    void accountCleanupDoesNotSilentlySkipAssetReservedForCheerPurge() {
        MediaAsset asset = MediaAsset.builder().id(60L).ownerUserId(7L).domain(MediaDomain.CHEER)
                .status(MediaAssetStatus.CHEER_PURGE_PENDING).objectKey("media/cheer/7/synthetic.webp").build();
        when(mediaAssetRepository.findByOwnerUserIdAndIdGreaterThanOrderByIdAsc(
                eq(7L), eq(0L), any(Pageable.class))).thenReturn(List.of(asset));
        org.mockito.Mockito.doReturn(Optional.empty()).when(finalizeClaimService)
                .claimOwnedAssetForAccountDeletion(asset, 7L);
        when(mediaAssetRepository.findByIdAndOwnerUserId(60L, 7L)).thenReturn(Optional.of(asset));

        assertThrows(com.example.common.exception.ServiceUnavailableBusinessException.class,
                () -> mediaUploadService.deleteOwnedAssetsForAccount(7L, Set.of()));
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
        verify(mediaAssetLinkRepository, never()).deleteByAssetId(60L);
    }

    @Test
    void accountCleanupCountsAssetWhoseClaimFailsForNonPurgeReason() {
        MediaAsset asset = MediaAsset.builder().id(61L).ownerUserId(7L).domain(MediaDomain.PROFILE)
                .status(MediaAssetStatus.READY).objectKey("media/profile/7/61.webp")
                .uploadExpiresAt(LocalDateTime.now().plusHours(1)).build();
        when(mediaAssetRepository.findByOwnerUserIdAndIdGreaterThanOrderByIdAsc(
                eq(7L), eq(0L), any(Pageable.class))).thenReturn(List.of(asset));
        org.mockito.Mockito.doReturn(Optional.empty()).when(finalizeClaimService)
                .claimOwnedAssetForAccountDeletion(asset, 7L);
        when(mediaAssetRepository.findByIdAndOwnerUserId(61L, 7L)).thenReturn(Optional.of(asset));

        assertEquals(1, mediaUploadService.deleteOwnedAssetsForAccount(7L, Set.of()));
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
        verify(mediaAssetLinkRepository, never()).deleteByAssetId(61L);
    }

    @Test
    @DisplayName("계정 삭제는 부모 claim 뒤에 생성된 파생 프로필 이미지를 다시 조회해 정리한다")
    void deleteOwnedAssetsForAccountRechecksDerivativeAfterClaimingParent() {
        MediaAsset parent = MediaAsset.builder()
                .id(57L)
                .ownerUserId(7L)
                .domain(MediaDomain.PROFILE)
                .status(MediaAssetStatus.READY)
                .originalFileName("avatar.png")
                .declaredContentType("image/png")
                .declaredBytes(10L)
                .declaredWidth(10)
                .declaredHeight(10)
                .stagingObjectKey("media/staging/profile/7/57-avatar.png")
                .objectKey("media/profile/7/57/avatar.webp")
                .uploadExpiresAt(LocalDateTime.now().plusHours(1))
                .build();
        MediaAsset derivative = MediaAsset.builder()
                .id(58L)
                .ownerUserId(7L)
                .domain(MediaDomain.PROFILE)
                .status(MediaAssetStatus.READY)
                .originalFileName("avatar.png")
                .declaredContentType("image/png")
                .declaredBytes(10L)
                .declaredWidth(10)
                .declaredHeight(10)
                .stagingObjectKey("media/staging/profile/7/57-avatar.png")
                .objectKey("media/profile-feed/7/57/avatar.webp")
                .derivedFrom(parent)
                .uploadExpiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(mediaAssetRepository.findByOwnerUserIdAndIdGreaterThanOrderByIdAsc(
                eq(7L), eq(0L), any(Pageable.class))).thenReturn(List.of(parent));
        when(mediaAssetRepository.findByDerivedFrom_Id(57L)).thenAnswer(invocation ->
                parent.getStatus() == MediaAssetStatus.DELETED ? Optional.of(derivative) : Optional.empty());
        when(storageConfig.getProfileBucket()).thenReturn("profile-bucket");
        when(storageStrategy.deleteChecked(eq("profile-bucket"), anyString())).thenReturn(Mono.empty());
        when(storageStrategy.exists(eq("profile-bucket"), anyString())).thenReturn(Mono.just(false));

        mediaUploadService.deleteOwnedAssetsForAccount(7L, Set.of());

        assertEquals(MediaAssetStatus.DELETED, parent.getStatus());
        assertEquals(MediaAssetStatus.DELETED, derivative.getStatus());
        assertNull(derivative.getOwnerUserId());
        verify(finalizeClaimService).claimOwnedAssetForAccountDeletion(derivative, 7L);
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
    }

    @Test
    @DisplayName("계정 삭제가 PUT 중 fencing token을 폐기하면 늦은 finalize는 READY로 커밋하지 않는다")
    void accountDeletionFencesAnInFlightFinalizePut() throws Exception {
        byte[] originalBytes = new byte[] {1, 2, 3, 4};
        byte[] optimizedBytes = new byte[] {5, 6};
        MediaAsset asset = pendingDiaryAsset(56L);
        asset.setDomain(MediaDomain.CHAT);
        asset.setStagingObjectKey("media/staging/chat/9/56-diary.png");
        CountDownLatch uploadStarted = new CountDownLatch(1);
        CountDownLatch allowUploadToFinish = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        when(mediaAssetRepository.findByIdAndOwnerUserId(56L, 9L)).thenReturn(Optional.of(asset));
        when(mediaAssetRepository.findByOwnerUserIdAndIdGreaterThanOrderByIdAsc(
                eq(9L), eq(0L), any(Pageable.class))).thenReturn(List.of(asset));
        when(storageConfig.getCheerBucket()).thenReturn("diary-bucket");
        when(storageStrategy.exists("diary-bucket", asset.getStagingObjectKey())).thenReturn(Mono.just(true));
        when(storageStrategy.head("diary-bucket", asset.getStagingObjectKey()))
                .thenReturn(Mono.just(new StoredObjectMetadata(4L, "image/png")));
        when(storageStrategy.download("diary-bucket", asset.getStagingObjectKey()))
                .thenReturn(Mono.just(new StoredObject(originalBytes, "image/png")));
        when(validationService.getActualDimension(originalBytes)).thenReturn(new ImageUtil.ImageDimension(12, 12));
        when(validationService.getActualDimension(optimizedBytes)).thenReturn(new ImageUtil.ImageDimension(10, 10));
        when(imageUtil.process(any(), eq("media_chat")))
                .thenReturn(new ImageUtil.ProcessedImage(optimizedBytes, "image/webp", "webp"));
        when(storageStrategy.uploadBytes(any(), any(), eq("diary-bucket"), any())).thenAnswer(invocation -> {
            uploadStarted.countDown();
            if (!allowUploadToFinish.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release the blocked test upload.");
            }
            return Mono.just("ok");
        });
        when(storageStrategy.deleteChecked(eq("diary-bucket"), anyString())).thenReturn(Mono.empty());
        when(mediaAssetRepository.save(any(MediaAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        try {
            Future<FinalizeMediaUploadResponse> finalization = executor.submit(
                    () -> mediaUploadService.finalizeUpload(9L, 56L));
            assertTrue(uploadStarted.await(5, TimeUnit.SECONDS));

            mediaUploadService.deleteOwnedAssetsForAccount(9L, Set.of());
            assertEquals(MediaAssetStatus.DELETED, asset.getStatus());
            assertEquals("media/chat/9/56/" + testToken(56L) + ".webp", asset.getFinalizeObjectKey());

            allowUploadToFinish.countDown();
            ExecutionException failure = assertThrows(ExecutionException.class, () -> finalization.get(5, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof ServiceUnavailableBusinessException);
            assertEquals("MEDIA_FINALIZATION_CLAIM_LOST",
                    ((ServiceUnavailableBusinessException) failure.getCause()).getCode());
            assertEquals(MediaAssetStatus.DELETED, asset.getStatus());
            assertEquals(testToken(100_056L), asset.getFinalizeToken());
        } finally {
            allowUploadToFinish.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("삭제 tombstone은 두 차례 absence 확인 후에만 저장소 키를 비운다")
    void cleanupDeletedFinalizeTombstonesRequiresQuiescenceChecks() {
        MediaAsset asset = pendingDiaryAsset(55L);
        asset.setStatus(MediaAssetStatus.DELETED);
        asset.setOwnerUserId(null);
        asset.setFinalizeToken(testToken(55L));
        asset.setFinalizeLeaseUntil(LocalDateTime.now().minusMinutes(1));
        asset.setFinalizeObjectKey("media/diary/9/55/attempt.webp");
        when(storageConfig.getMediaCleanupBatchSize()).thenReturn(10);
        when(storageConfig.getDiaryBucket()).thenReturn("diary-bucket");
        when(mediaAssetRepository
                .findByStatusAndFinalizeTokenIsNotNullAndFinalizeLeaseUntilBeforeOrderByFinalizeLeaseUntilAscIdAsc(
                        eq(MediaAssetStatus.DELETED), any(LocalDateTime.class), eq(PageRequest.of(0, 10))))
                .thenReturn(List.of(asset), List.of(asset));
        when(storageStrategy.deleteChecked(eq("diary-bucket"), anyString())).thenReturn(Mono.empty());
        when(storageStrategy.exists(eq("diary-bucket"), anyString())).thenReturn(Mono.just(false));

        mediaUploadService.cleanupDeletedFinalizeTombstones();

        assertNotNull(asset.getFinalizeCleanupVerifiedAt());
        verify(finalizeAttemptCleanupService).cleanupForAsset(55L);
        verify(finalizeClaimService, never()).clearDeletedTombstone(any());

        asset.setFinalizeCleanupVerifiedAt(LocalDateTime.now().minusMinutes(16));
        mediaUploadService.cleanupDeletedFinalizeTombstones();

        verify(finalizeAttemptCleanupService, times(2)).cleanupForAsset(55L);
        verify(finalizeClaimService).clearDeletedTombstone(any());
        assertNull(asset.getFinalizeToken());
        assertNull(asset.getFinalizeObjectKey());
        assertEquals(MediaAsset.CLEARED_STAGING_OBJECT_KEY, asset.getStagingObjectKey());
    }

    @Test
    void cleanupDeletedTombstoneRemovesLateStagingObjectBeforeClearingKey() {
        MediaAsset asset = pendingDiaryAsset(57L);
        asset.setStatus(MediaAssetStatus.DELETED);
        asset.setOwnerUserId(null);
        asset.setFinalizeToken(testToken(57L));
        asset.setFinalizeLeaseUntil(LocalDateTime.now().minusMinutes(1));
        asset.setUploadExpiresAt(LocalDateTime.now().minusMinutes(20));
        String stagingKey = asset.getStagingObjectKey();
        java.util.concurrent.atomic.AtomicBoolean objectExists = new java.util.concurrent.atomic.AtomicBoolean(false);
        when(storageConfig.getMediaCleanupBatchSize()).thenReturn(10);
        when(storageConfig.getDiaryBucket()).thenReturn("diary-bucket");
        when(mediaAssetRepository
                .findByStatusAndFinalizeTokenIsNotNullAndFinalizeLeaseUntilBeforeOrderByFinalizeLeaseUntilAscIdAsc(
                        eq(MediaAssetStatus.DELETED), any(LocalDateTime.class), eq(PageRequest.of(0, 10))))
                .thenReturn(List.of(asset));
        when(storageStrategy.deleteChecked(eq("diary-bucket"), eq(stagingKey))).thenAnswer(invocation -> {
            objectExists.set(false);
            return Mono.empty();
        });
        when(storageStrategy.exists(eq("diary-bucket"), eq(stagingKey)))
                .thenAnswer(invocation -> Mono.just(objectExists.get()));

        mediaUploadService.cleanupDeletedFinalizeTombstones();
        assertNotNull(asset.getFinalizeCleanupVerifiedAt());
        objectExists.set(true); // Synthetic late PUT after the first absence check.
        asset.setFinalizeCleanupVerifiedAt(LocalDateTime.now().minusMinutes(16));

        mediaUploadService.cleanupDeletedFinalizeTombstones();

        org.junit.jupiter.api.Assertions.assertFalse(objectExists.get());
        verify(storageStrategy, times(2)).deleteChecked("diary-bucket", stagingKey);
        verify(finalizeClaimService).clearDeletedTombstone(any());
        assertEquals(MediaAsset.CLEARED_STAGING_OBJECT_KEY, asset.getStagingObjectKey());
    }

    @Test
    @DisplayName("채팅 orphan 정리는 삭제 예약을 보존하고 객체 부재를 확인한다")
    void cleanupUnlinkedReadyAssets_claimsTombstoneThenDeletesObject() {
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
        when(finalizeClaimService.claimUnlinkedMediaForCleanup(eq(61L), eq(4L), eq(MediaDomain.CHAT), any()))
                .thenAnswer(invocation -> {
            asset.setStatus(MediaAssetStatus.DELETED);
            asset.setFinalizeToken("synthetic-token");
            return Optional.of(new MediaFinalizeClaimService.FinalizeCleanupClaim(asset, "synthetic-token"));
        });
        when(storageStrategy.deleteChecked("chat-bucket", asset.getObjectKey())).thenReturn(Mono.empty());
        when(storageStrategy.exists("chat-bucket", asset.getObjectKey())).thenReturn(Mono.just(false));

        MediaCleanupTargetReport report = mediaUploadService.cleanupUnlinkedReadyAssets();

        assertEquals(MediaAssetStatus.DELETED, asset.getStatus());
        assertEquals(MediaCleanupTarget.ORPHAN, report.target());
        assertEquals(1, report.scannedCount());
        assertEquals(1, report.deletedCount());
        assertEquals(0, report.errorCount());
        verify(finalizeClaimService).claimUnlinkedMediaForCleanup(eq(61L), eq(4L), eq(MediaDomain.CHAT), any());
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

    private static String testToken(Long assetId) {
        return String.format("00000000-0000-0000-0000-%012d", assetId);
    }

    @Test
    @DisplayName("staging object가 실제로 없으면(404) finalize는 NotFound로 실패하고 claim을 해제한다")
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
        assertEquals(MediaAssetStatus.PENDING, asset.getStatus());
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
    @DisplayName("검증 실패 때 공유 staging 삭제를 시도하지 않고 재시도 상태를 보존한다")
    void finalizeUpload_validationFailureKeepsStagingForRetry() {
        MediaAsset asset = pendingDiaryAsset(76L);
        StoredObjectMetadata metadata = new StoredObjectMetadata(4L, "image/png");
        when(mediaAssetRepository.findByIdAndOwnerUserId(76L, 9L)).thenReturn(Optional.of(asset));
        when(storageConfig.getDiaryBucket()).thenReturn("diary-bucket");
        when(storageStrategy.exists("diary-bucket", asset.getStagingObjectKey())).thenReturn(Mono.just(true));
        when(storageStrategy.head("diary-bucket", asset.getStagingObjectKey())).thenReturn(Mono.just(metadata));
        doThrow(new BadRequestBusinessException("INVALID_MEDIA_FILE_SIZE", "too large"))
                .when(validationService).validateStoredObjectMetadata(asset, metadata);
        assertThrows(BadRequestBusinessException.class, () -> mediaUploadService.finalizeUpload(9L, 76L));

        assertEquals(MediaAssetStatus.PENDING, asset.getStatus());
        verify(storageStrategy, never()).deleteChecked("diary-bucket", asset.getStagingObjectKey());
        verify(finalizeAttemptCleanupService).cleanupAfterFailedFinalize(any());
        verify(mediaAssetRepository, never()).save(any(MediaAsset.class));
    }

    @Test
    @DisplayName("사용자 delete에서 스토리지 삭제가 실패하면 503이고 키를 보존한 tombstone으로 재시도한다")
    void deleteUpload_storageFailureLeavesRetryableTombstone() {
        MediaAsset asset = pendingDiaryAsset(77L);
        when(mediaAssetRepository.findByIdAndOwnerUserId(77L, 9L)).thenReturn(Optional.of(asset));
        when(mediaAssetLinkRepository.existsByAssetId(77L)).thenReturn(false);
        when(storageConfig.getDiaryBucket()).thenReturn("diary-bucket");
        when(storageStrategy.deleteChecked("diary-bucket", asset.getStagingObjectKey()))
                .thenReturn(Mono.error(storageFailure(StorageOperationException.Kind.TRANSIENT)));

        assertThrows(ServiceUnavailableBusinessException.class, () -> mediaUploadService.deleteUpload(9L, 77L));

        assertEquals(MediaAssetStatus.DELETED, asset.getStatus());
        assertNotNull(asset.getFinalizeToken());
        assertEquals(asset.getStagingObjectKey(), "media/staging/diary/9/77-diary.png");
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

        assertEquals(MediaAssetStatus.FINALIZING, asset.getStatus());
        assertEquals(0, report.deletedCount());
        assertEquals(1, report.errorCount());
        verify(metricsService).recordMediaCleanup("pending", "error");
    }

    @Test
    @DisplayName("채팅 orphan 삭제 실패 시 비연결 tombstone과 객체 경로를 남긴다")
    void cleanupUnlinkedReadyAssets_deleteFailureKeepsTombstone() {
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
        when(finalizeClaimService.claimUnlinkedMediaForCleanup(eq(79L), eq(4L), eq(MediaDomain.CHAT), any()))
                .thenAnswer(invocation -> {
                    asset.setStatus(MediaAssetStatus.DELETED);
                    asset.setFinalizeToken("synthetic-token");
                    return Optional.of(new MediaFinalizeClaimService.FinalizeCleanupClaim(asset, "synthetic-token"));
                });
        when(storageStrategy.deleteChecked("chat-bucket", asset.getObjectKey()))
                .thenReturn(Mono.error(storageFailure(StorageOperationException.Kind.TRANSIENT)));

        MediaCleanupTargetReport report = mediaUploadService.cleanupUnlinkedReadyAssets();

        assertEquals(MediaAssetStatus.DELETED, asset.getStatus());
        assertEquals("media/chat/4/79.webp", asset.getObjectKey());
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
        when(storageStrategy.deleteChecked(eq("diary-bucket"), anyString())).thenReturn(Mono.empty());

        assertThrows(ServiceUnavailableBusinessException.class, () -> mediaUploadService.finalizeUpload(9L, 80L));

        assertEquals(MediaAssetStatus.PENDING, asset.getStatus());
        verify(storageStrategy, never()).deleteChecked("diary-bucket", asset.getStagingObjectKey());
        verify(mediaAssetRepository, never()).save(any(MediaAsset.class));
        verify(metricsService).recordMediaFinalize("DIARY", "storage_unavailable");
    }
}
