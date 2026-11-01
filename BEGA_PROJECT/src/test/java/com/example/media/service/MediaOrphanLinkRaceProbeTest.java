package com.example.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.strategy.StorageStrategy;
import com.example.common.image.ImageOptimizationMetricsService;
import com.example.common.image.ImageUtil;
import com.example.media.entity.MediaAsset;
import com.example.media.entity.MediaAssetStatus;
import com.example.media.entity.MediaAssetLink;
import com.example.media.entity.MediaDomain;
import com.example.media.entity.MediaLinkRole;
import com.example.media.repository.MediaAssetLinkRepository;
import com.example.media.repository.MediaAssetRepository;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

@DataJpaTest
@Import({MediaUploadService.class, MediaLinkService.class, MediaFinalizeClaimService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MediaOrphanLinkRaceProbeTest {
    @Autowired MediaUploadService uploadService;
    @Autowired MediaLinkService linkService;
    @Autowired MediaFinalizeClaimService claimService;
    @MockitoSpyBean MediaAssetRepository assets;
    @Autowired MediaAssetLinkRepository links;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean StorageStrategy storage;
    @MockitoBean StorageConfig config;
    @MockitoBean MediaFinalizeAttemptCleanupService attemptCleanupService;
    @MockitoBean MediaUploadValidationService validationService;
    @MockitoBean MediaQuotaService quotaService;
    @MockitoBean MediaRateLimitService rateLimitService;
    @MockitoBean ImageUtil imageUtil;
    @MockitoBean ImageOptimizationMetricsService metricsService;

    @BeforeEach
    void clearSyntheticAssets() {
        links.deleteAll();
        jdbc.update("UPDATE media_assets SET derived_from_asset_id = NULL WHERE derived_from_asset_id IS NOT NULL");
        assets.deleteAll();
    }

    @Test
    void linkingAfterOrphanSelectionMustNotDeleteLinkedObject() throws Exception {
        var tx = new TransactionTemplate(transactionManager);
        Long id = tx.execute(status -> assets.saveAndFlush(MediaAsset.builder()
                .ownerUserId(7L).domain(MediaDomain.SEAT_VIEW).status(MediaAssetStatus.READY)
                .objectKey("media/seat-view/7/probe.webp").stagingObjectKey("staging/probe")
                .declaredContentType("image/webp").declaredBytes(10L).declaredWidth(1).declaredHeight(1)
                .createdAt(LocalDateTime.now().minusDays(2)).uploadExpiresAt(LocalDateTime.now().minusDays(1))
                .build()).getId());
        var selected = new CountDownLatch(1);
        var resume = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object result = assets.findUnlinkedAssetsOlderThan(invocation.getArgument(0), invocation.getArgument(1));
            selected.countDown();
            if (!resume.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("probe timed out");
            return result;
        }).when(assets).findUnlinkedAssetsOlderThan(any(), any(), any(Pageable.class));
        when(config.getMediaOrphanRetentionHours()).thenReturn(24);
        when(config.getMediaCleanupBatchSize()).thenReturn(100);
        when(config.getDiaryBucket()).thenReturn("synthetic-bucket");
        when(storage.deleteChecked(any(), any())).thenReturn(Mono.empty());
        var executor = Executors.newSingleThreadExecutor();
        try {
            var cleanup = executor.submit(() -> uploadService.cleanupUnlinkedReadyAssets());
            if (!selected.await(30, TimeUnit.SECONDS)) {
                cleanup.get(1, TimeUnit.SECONDS);
                throw new AssertionError("cleanup did not reach orphan selection");
            }
            linkService.linkSeatViewAsset(7L, "media/seat-view/7/probe.webp", 501L);
            resume.countDown();
            cleanup.get(30, TimeUnit.SECONDS);
            assertThat(links.existsByAssetId(id)).isTrue();
            assertThat(assets.findById(id).orElseThrow().getStatus()).isEqualTo(MediaAssetStatus.READY);
            org.mockito.Mockito.verify(storage, org.mockito.Mockito.never()).deleteChecked(any(), any());
        } finally {
            resume.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void committedSeatViewOrphanClaimRejectsLinkBeforeStorageCompletes() throws Exception {
        String key = "media/seat-view/7/orphan-first.webp";
        Long id = new TransactionTemplate(transactionManager).execute(status -> assets.saveAndFlush(MediaAsset.builder()
                .ownerUserId(7L).domain(MediaDomain.SEAT_VIEW).status(MediaAssetStatus.READY)
                .objectKey(key).stagingObjectKey("staging/orphan-first")
                .declaredContentType("image/webp").declaredBytes(10L).declaredWidth(1).declaredHeight(1)
                .createdAt(LocalDateTime.now().minusDays(2)).uploadExpiresAt(LocalDateTime.now().minusDays(1))
                .build()).getId());
        when(config.getMediaOrphanRetentionHours()).thenReturn(24);
        when(config.getMediaCleanupBatchSize()).thenReturn(100);
        when(config.getDiaryBucket()).thenReturn("synthetic-bucket");
        CountDownLatch storageStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(storage.deleteChecked(any(), any())).thenReturn(Mono.fromRunnable(() -> {
            storageStarted.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("storage timeout");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
        }));
        when(storage.exists(any(), any())).thenReturn(Mono.just(false));
        when(attemptCleanupService.cleanupForAsset(id)).thenReturn(true);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var cleanup = executor.submit(() -> uploadService.cleanupUnlinkedReadyAssets());
            try {
                assertThat(storageStarted.await(5, TimeUnit.SECONDS)).isTrue();
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> linkService.linkSeatViewAsset(7L, key, 502L))
                        .isInstanceOf(com.example.common.exception.BadRequestBusinessException.class);
            } finally {
                release.countDown();
            }
            cleanup.get(10, TimeUnit.SECONDS);
        }
        assertThat(links.existsByAssetId(id)).isFalse();
        assertThat(assets.findById(id).orElseThrow().getStatus()).isEqualTo(MediaAssetStatus.DELETED);
    }

    @Test
    void failedSeatViewOrphanStorageDeletionKeepsRetryableTombstone() {
        String key = "media/seat-view/7/failing.webp";
        Long id = new TransactionTemplate(transactionManager).execute(status -> assets.saveAndFlush(MediaAsset.builder()
                .ownerUserId(7L).domain(MediaDomain.SEAT_VIEW).status(MediaAssetStatus.READY)
                .objectKey(key).stagingObjectKey("staging/failing")
                .declaredContentType("image/webp").declaredBytes(10L).declaredWidth(1).declaredHeight(1)
                .createdAt(LocalDateTime.now().minusDays(2)).uploadExpiresAt(LocalDateTime.now().minusDays(1))
                .build()).getId());
        when(config.getMediaOrphanRetentionHours()).thenReturn(24);
        when(config.getMediaCleanupBatchSize()).thenReturn(100);
        when(config.getDiaryBucket()).thenReturn("synthetic-bucket");
        when(storage.deleteChecked(any(), any())).thenReturn(Mono.error(new IllegalStateException("synthetic failure")));

        var report = uploadService.cleanupUnlinkedReadyAssets();
        MediaAsset preserved = assets.findById(id).orElseThrow();
        assertThat(report.errorCount()).isGreaterThanOrEqualTo(1);
        assertThat(preserved.getStatus()).isEqualTo(MediaAssetStatus.DELETED);
        assertThat(preserved.getObjectKey()).isEqualTo(key);
        assertThat(preserved.getStagingObjectKey()).isEqualTo("staging/failing");
        assertThat(preserved.getFinalizeToken()).isNotBlank();
        assertThat(preserved.getFinalizeLeaseUntil()).isNotNull();
    }

    @Test
    void ownerlessReadyOrphanCanStillBeClaimedAndCleaned() {
        Long id = new TransactionTemplate(transactionManager).execute(status -> assets.saveAndFlush(MediaAsset.builder()
                .ownerUserId(null).domain(MediaDomain.CHAT).status(MediaAssetStatus.READY)
                .objectKey("media/chat/legacy/ownerless.webp").stagingObjectKey("staging/ownerless")
                .declaredContentType("image/webp").declaredBytes(10L).declaredWidth(1).declaredHeight(1)
                .createdAt(LocalDateTime.now().minusDays(2)).uploadExpiresAt(LocalDateTime.now().minusDays(1))
                .build()).getId());
        when(config.getMediaOrphanRetentionHours()).thenReturn(24);
        when(config.getMediaCleanupBatchSize()).thenReturn(100);
        when(config.getCheerBucket()).thenReturn("synthetic-chat");
        when(storage.deleteChecked(any(), any())).thenReturn(Mono.empty());
        when(storage.exists(any(), any())).thenReturn(Mono.just(false));
        when(attemptCleanupService.cleanupForAsset(id)).thenReturn(true);

        uploadService.cleanupUnlinkedReadyAssets();

        assertThat(assets.findById(id).orElseThrow().getStatus()).isEqualTo(MediaAssetStatus.DELETED);
    }

    @ParameterizedTest
    @EnumSource(value = MediaDomain.class, names = {"DIARY", "CHAT"})
    void diaryAndChatLinkAfterCandidateSelectionPreventsDeletion(MediaDomain domain) throws Exception {
        String key = "media/" + domain.getPathSegment() + "/7/selection.webp";
        Long id = createReadyAsset(domain, key, "staging/selection");
        CountDownLatch selected = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        doAnswer(invocation -> {
            var result = assets.findUnlinkedAssetsOlderThan(invocation.getArgument(0), invocation.getArgument(1));
            selected.countDown();
            if (!resume.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("selection timeout");
            return result;
        }).when(assets).findUnlinkedAssetsOlderThan(any(), any(), any(Pageable.class));
        when(config.getMediaOrphanRetentionHours()).thenReturn(24);
        when(config.getMediaCleanupBatchSize()).thenReturn(100);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var cleanup = executor.submit(() -> uploadService.cleanupUnlinkedReadyAssets());
            try {
                assertThat(selected.await(5, TimeUnit.SECONDS)).isTrue();
                linkDomainAsset(domain, key);
            } finally {
                resume.countDown();
            }
            cleanup.get(10, TimeUnit.SECONDS);
        }
        assertThat(links.existsByAssetId(id)).isTrue();
        assertThat(assets.findById(id).orElseThrow().getStatus()).isEqualTo(MediaAssetStatus.READY);
        org.mockito.Mockito.verify(storage, org.mockito.Mockito.never()).deleteChecked(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = MediaDomain.class, names = {"DIARY", "CHAT"})
    void diaryAndChatOrphanClaimBlocksLinkDuringStorage(MediaDomain domain) throws Exception {
        String key = "media/" + domain.getPathSegment() + "/7/claim-first.webp";
        Long id = createReadyAsset(domain, key, "staging/claim-first");
        CountDownLatch storageStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(config.getMediaOrphanRetentionHours()).thenReturn(24);
        when(config.getMediaCleanupBatchSize()).thenReturn(100);
        when(config.getDiaryBucket()).thenReturn("synthetic-diary");
        when(config.getCheerBucket()).thenReturn("synthetic-chat");
        when(storage.deleteChecked(any(), any())).thenReturn(Mono.fromRunnable(() -> {
            storageStarted.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("storage timeout");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
        }));
        when(storage.exists(any(), any())).thenReturn(Mono.just(false));
        when(attemptCleanupService.cleanupForAsset(id)).thenReturn(true);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var cleanup = executor.submit(() -> uploadService.cleanupUnlinkedReadyAssets());
            try {
                assertThat(storageStarted.await(5, TimeUnit.SECONDS)).isTrue();
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> linkDomainAsset(domain, key))
                        .isInstanceOf(com.example.common.exception.BadRequestBusinessException.class);
            } finally {
                release.countDown();
            }
            cleanup.get(10, TimeUnit.SECONDS);
        }
        assertThat(links.existsByAssetId(id)).isFalse();
        assertThat(assets.findById(id).orElseThrow().getStatus()).isEqualTo(MediaAssetStatus.DELETED);
    }

    @ParameterizedTest
    @EnumSource(value = MediaDomain.class, names = {"DIARY", "CHAT"})
    void diaryAndChatUploadDeletionClaimRejectsLinkedAsset(MediaDomain domain) {
        String key = "media/" + domain.getPathSegment() + "/7/linked-upload.webp";
        Long id = createReadyAsset(domain, key, "staging/linked-upload");
        linkDomainAsset(domain, key);
        MediaAsset asset = assets.findById(id).orElseThrow();
        assertThat(claimService.claimOwnedAssetForDeletion(asset, 7L)).isEmpty();
        assertThat(assets.findById(id).orElseThrow().getStatus()).isEqualTo(MediaAssetStatus.READY);
        assertThat(links.existsByAssetId(id)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = MediaDomain.class, names = {"DIARY", "CHAT"})
    void diaryAndChatStorageFailureKeepsRetryableTombstone(MediaDomain domain) {
        String key = "media/" + domain.getPathSegment() + "/7/failing.webp";
        Long id = createReadyAsset(domain, key, "staging/domain-failure");
        when(config.getMediaOrphanRetentionHours()).thenReturn(24);
        when(config.getMediaCleanupBatchSize()).thenReturn(100);
        when(config.getDiaryBucket()).thenReturn("synthetic-diary");
        when(config.getCheerBucket()).thenReturn("synthetic-chat");
        when(storage.deleteChecked(any(), any())).thenReturn(Mono.error(new IllegalStateException("synthetic failure")));

        var report = uploadService.cleanupUnlinkedReadyAssets();
        MediaAsset preserved = assets.findById(id).orElseThrow();
        assertThat(report.errorCount()).isGreaterThanOrEqualTo(1);
        assertThat(preserved.getStatus()).isEqualTo(MediaAssetStatus.DELETED);
        assertThat(preserved.getObjectKey()).isEqualTo(key);
        assertThat(preserved.getStagingObjectKey()).isEqualTo("staging/domain-failure");
        assertThat(preserved.getFinalizeToken()).isNotBlank();
    }

    @Test
    void profileAndFeedLinkedAfterOrphanSelectionMustBothSurvive() throws Exception {
        String primaryKey = "media/profile/7/selection.webp";
        Long[] ids = createProfilePair(primaryKey, "media/profile-feed/7/selection.webp");
        CountDownLatch selected = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        doAnswer(invocation -> {
            var result = assets.findUnlinkedAssetsOlderThan(invocation.getArgument(0), invocation.getArgument(1));
            selected.countDown();
            if (!resume.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("selection timeout");
            return result;
        }).when(assets).findUnlinkedAssetsOlderThan(any(), any(), any(Pageable.class));
        when(config.getMediaOrphanRetentionHours()).thenReturn(24);
        when(config.getMediaCleanupBatchSize()).thenReturn(100);
        when(config.getProfileBucket()).thenReturn("synthetic-profile");
        when(storage.deleteChecked(any(), any())).thenReturn(Mono.empty());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var cleanup = executor.submit(() -> uploadService.cleanupUnlinkedReadyAssets());
            try {
                assertThat(selected.await(5, TimeUnit.SECONDS)).isTrue();
                linkService.syncProfileLinks(7L, primaryKey);
            } finally {
                resume.countDown();
            }
            cleanup.get(10, TimeUnit.SECONDS);
        }
        assertThat(links.existsByAssetId(ids[0])).isTrue();
        assertThat(links.existsByAssetId(ids[1])).isTrue();
        assertThat(assets.findById(ids[0]).orElseThrow().getStatus()).isEqualTo(MediaAssetStatus.READY);
        assertThat(assets.findById(ids[1]).orElseThrow().getStatus()).isEqualTo(MediaAssetStatus.READY);
        org.mockito.Mockito.verify(storage, org.mockito.Mockito.never()).deleteChecked(any(), any());
    }

    @Test
    void profileOrphanClaimPreventsLinkWhileStorageIsBlocked() throws Exception {
        String primaryKey = "media/profile/7/claim-first.webp";
        Long[] ids = createProfilePair(primaryKey, "media/profile-feed/7/claim-first.webp");
        when(config.getMediaOrphanRetentionHours()).thenReturn(24);
        when(config.getMediaCleanupBatchSize()).thenReturn(100);
        when(config.getProfileBucket()).thenReturn("synthetic-profile");
        CountDownLatch storageStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(storage.deleteChecked(any(), any())).thenReturn(Mono.fromRunnable(() -> {
            storageStarted.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("storage timeout");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
        }));
        when(storage.exists(any(), any())).thenReturn(Mono.just(false));
        when(attemptCleanupService.cleanupForAsset(org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var cleanup = executor.submit(() -> uploadService.cleanupUnlinkedReadyAssets());
            try {
                assertThat(storageStarted.await(5, TimeUnit.SECONDS)).isTrue();
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> linkService.syncProfileLinks(7L, primaryKey))
                        .isInstanceOf(com.example.common.exception.BadRequestBusinessException.class);
            } finally {
                release.countDown();
            }
            cleanup.get(10, TimeUnit.SECONDS);
        }
        assertThat(links.existsByAssetId(ids[0])).isFalse();
        assertThat(links.existsByAssetId(ids[1])).isFalse();
    }

    @Test
    void unlinkedFeedIsNotPurgedWhileItsPrimaryRemainsLinked() {
        Long[] ids = createProfilePair("media/profile/7/legacy.webp", "media/profile-feed/7/legacy.webp");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            MediaAsset primary = assets.findById(ids[0]).orElseThrow();
            links.saveAndFlush(MediaAssetLink.builder().asset(primary).assetId(primary.getId())
                    .domain(MediaDomain.PROFILE).entityId(7L).role(MediaLinkRole.PROFILE_PRIMARY).build());
        });
        when(config.getMediaOrphanRetentionHours()).thenReturn(24);
        when(config.getMediaCleanupBatchSize()).thenReturn(100);

        uploadService.cleanupUnlinkedReadyAssets();

        assertThat(assets.findById(ids[1]).orElseThrow().getStatus()).isEqualTo(MediaAssetStatus.READY);
        org.mockito.Mockito.verify(storage, org.mockito.Mockito.never()).deleteChecked(any(), any());
    }

    @Test
    void unlinkedFeedCannotBeExplicitlyDeletedWhilePrimaryRemainsLinked() {
        Long[] ids = createProfilePair("media/profile/7/explicit.webp", "media/profile-feed/7/explicit.webp");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            MediaAsset primary = assets.findById(ids[0]).orElseThrow();
            links.saveAndFlush(MediaAssetLink.builder().asset(primary).assetId(primary.getId())
                    .domain(MediaDomain.PROFILE).entityId(7L).role(MediaLinkRole.PROFILE_PRIMARY).build());
        });
        MediaAsset feed = assets.findById(ids[1]).orElseThrow();

        assertThat(claimService.claimOwnedAssetForDeletion(feed, 7L)).isEmpty();
        assertThat(assets.findById(ids[1]).orElseThrow().getStatus()).isEqualTo(MediaAssetStatus.READY);
    }

    @Test
    void feedCannotBeSelectedAfterItsPrimaryHasBeenClaimed() {
        String feedKey = "media/profile-feed/7/claimed-parent.webp";
        Long[] ids = createProfilePair("media/profile/7/claimed-parent.webp", feedKey);
        jdbc.update("UPDATE media_assets SET status = ? WHERE id = ?", "DELETED", ids[0]);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> linkService.syncProfileLinks(7L, feedKey))
                .isInstanceOf(com.example.common.exception.BadRequestBusinessException.class);
        assertThat(links.existsByAssetId(ids[1])).isFalse();
    }

    @Test
    void failedProfileOrphanDeletionRetainsBothTombstonesAndKeys() {
        String primaryKey = "media/profile/7/failure.webp";
        String feedKey = "media/profile-feed/7/failure.webp";
        Long[] ids = createProfilePair(primaryKey, feedKey);
        when(config.getMediaOrphanRetentionHours()).thenReturn(24);
        when(config.getMediaCleanupBatchSize()).thenReturn(100);
        when(config.getProfileBucket()).thenReturn("synthetic-profile");
        when(storage.deleteChecked(any(), any())).thenReturn(Mono.error(new IllegalStateException("synthetic failure")));

        var report = uploadService.cleanupUnlinkedReadyAssets();

        assertThat(report.errorCount()).isEqualTo(2);
        MediaAsset primary = assets.findById(ids[0]).orElseThrow();
        MediaAsset feed = assets.findById(ids[1]).orElseThrow();
        assertThat(primary.getStatus()).isEqualTo(MediaAssetStatus.DELETED);
        assertThat(feed.getStatus()).isEqualTo(MediaAssetStatus.DELETED);
        assertThat(primary.getObjectKey()).isEqualTo(primaryKey);
        assertThat(feed.getObjectKey()).isEqualTo(feedKey);
        assertThat(primary.getFinalizeToken()).isNotBlank();
        assertThat(feed.getFinalizeToken()).isNotBlank();
    }

    private Long[] createProfilePair(String primaryKey, String feedKey) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            MediaAsset primary = assets.saveAndFlush(MediaAsset.builder()
                    .ownerUserId(7L).domain(MediaDomain.PROFILE).status(MediaAssetStatus.READY)
                    .objectKey(primaryKey).stagingObjectKey("staging/profile")
                    .declaredContentType("image/webp").declaredBytes(10L).declaredWidth(1).declaredHeight(1)
                    .createdAt(LocalDateTime.now().minusDays(2)).uploadExpiresAt(LocalDateTime.now().minusDays(1))
                    .build());
            MediaAsset feed = assets.saveAndFlush(MediaAsset.builder()
                    .ownerUserId(7L).domain(MediaDomain.PROFILE).status(MediaAssetStatus.READY)
                    .objectKey(feedKey).stagingObjectKey("staging/profile-feed")
                    .declaredContentType("image/webp").declaredBytes(10L).declaredWidth(1).declaredHeight(1)
                    .createdAt(LocalDateTime.now().minusDays(2)).uploadExpiresAt(LocalDateTime.now().minusDays(1))
                    .derivedFrom(primary).build());
            return new Long[] {primary.getId(), feed.getId()};
        });
    }

    private Long createReadyAsset(MediaDomain domain, String key, String staging) {
        return new TransactionTemplate(transactionManager).execute(status -> assets.saveAndFlush(MediaAsset.builder()
                .ownerUserId(7L).domain(domain).status(MediaAssetStatus.READY)
                .objectKey(key).stagingObjectKey(staging).declaredContentType("image/webp")
                .declaredBytes(10L).declaredWidth(1).declaredHeight(1)
                .createdAt(LocalDateTime.now().minusDays(2)).uploadExpiresAt(LocalDateTime.now().minusDays(1))
                .build()).getId());
    }

    private void linkDomainAsset(MediaDomain domain, String key) {
        if (domain == MediaDomain.DIARY) linkService.syncDiaryLinks(501L, 7L, java.util.List.of(key));
        else linkService.syncChatLink(501L, 7L, key);
    }
}
