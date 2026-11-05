package com.example.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.strategy.StorageStrategy;
import com.example.common.image.ImageOptimizationMetricsService;
import com.example.common.image.ImageUtil;
import com.example.media.entity.MediaAsset;
import com.example.media.entity.MediaAssetLink;
import com.example.media.entity.MediaAssetStatus;
import com.example.media.entity.MediaDomain;
import com.example.media.entity.MediaLinkRole;
import com.example.media.repository.MediaAssetLinkRepository;
import com.example.media.repository.MediaAssetRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cheer_purge_lifecycle;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=10000",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import({MediaUploadService.class, MediaLinkService.class, MediaFinalizeClaimService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CheerPurgeLifecycleIntegrationTest {
    private static final long OWNER_ID = 17L;
    private static final long POST_ID = 501L;
    private static final String OBJECT_KEY = "media/cheer/17/synthetic.webp";
    private static final String STAGING_KEY = "media/staging/cheer/17/synthetic.webp";
    private static final String BUCKET = "synthetic-cheer";
    private static final String PARENT_KEY = "media/cheer/17/parent.webp";
    private static final String CHILD_KEY = "media/cheer/17/child.webp";

    @Autowired private MediaUploadService uploadService;
    @Autowired private MediaLinkService linkService;
    @MockitoSpyBean private MediaAssetRepository assets;
    @Autowired private MediaAssetLinkRepository links;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private StorageStrategy storage;
    @MockitoBean private StorageConfig config;
    @MockitoBean private MediaFinalizeAttemptCleanupService attemptCleanupService;
    @MockitoBean private MediaUploadValidationService validationService;
    @MockitoBean private MediaQuotaService quotaService;
    @MockitoBean private MediaRateLimitService rateLimitService;
    @MockitoBean private ImageUtil imageUtil;
    @MockitoBean private ImageOptimizationMetricsService metricsService;

    @BeforeEach
    void setUp() {
        links.deleteAll();
        // Derived children go first so the parent foreign key is never violated.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assets.deleteAll(assets.findAll().stream().filter(asset -> asset.getDerivedFrom() != null).toList());
            assets.deleteAll();
        });
        when(config.getMediaOrphanRetentionHours()).thenReturn(24);
        when(config.getMediaCleanupBatchSize()).thenReturn(100);
        when(config.getCheerBucket()).thenReturn(BUCKET);
    }

    @Test
    void committedCheerLinkBeforeCleanupKeepsAssetAndObject() throws Exception {
        Long assetId = createReadyAsset();
        CountDownLatch workerReady = new CountDownLatch(1);
        CountDownLatch linkCommitted = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var cleanup = executor.submit(() -> {
                workerReady.countDown();
                if (!linkCommitted.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("link transaction did not complete");
                }
                return uploadService.cleanupUnlinkedReadyAssets();
            });
            try {
                assertThat(workerReady.await(5, TimeUnit.SECONDS)).isTrue();
                linkService.syncCheerLinks(POST_ID, OWNER_ID, List.of(OBJECT_KEY));
            } finally {
                linkCommitted.countDown();
            }
            cleanup.get(10, TimeUnit.SECONDS);
        }

        assertThat(links.findById(assetId)).hasValueSatisfying(link -> {
            assertThat(link.getDomain()).isEqualTo(MediaDomain.CHEER);
            assertThat(link.getEntityId()).isEqualTo(POST_ID);
        });
        MediaAsset asset = assets.findById(assetId).orElseThrow();
        assertThat(asset.getStatus()).isEqualTo(MediaAssetStatus.READY);
        assertThat(asset.getObjectKey()).isEqualTo(OBJECT_KEY);
        assertThat(asset.getStagingObjectKey()).isEqualTo(STAGING_KEY);
        verify(storage, never()).deleteChecked(anyString(), anyString());
    }

    @Test
    void accountCleanupCanProceedAfterOuterTransactionFlushesLinkRemoval() {
        Long assetId = createReadyAsset();
        linkService.syncCheerLinks(POST_ID, OWNER_ID, List.of(OBJECT_KEY));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            linkService.unlinkEntity(MediaDomain.CHEER, POST_ID);
            links.flush();
            uploadService.deleteOwnedAssetsForAccount(OWNER_ID, java.util.Set.of());
        });
        MediaAsset tombstone = assets.findById(assetId).orElseThrow();
        assertThat(tombstone.getStatus()).isEqualTo(MediaAssetStatus.DELETED);
        verify(storage, never()).deleteChecked(anyString(), anyString());
        tombstone.setFinalizeLeaseUntil(LocalDateTime.now().minusMinutes(1));
        assets.saveAndFlush(tombstone);
        when(storage.deleteChecked(anyString(), anyString())).thenReturn(Mono.empty());
        when(storage.exists(anyString(), anyString())).thenReturn(Mono.just(false));
        when(attemptCleanupService.cleanupForAsset(assetId)).thenReturn(true);
        uploadService.cleanupDeletedFinalizeTombstones();
        assertThat(assets.findById(assetId).orElseThrow().getFinalizeCleanupVerifiedAt()).isNotNull();
        verify(storage).deleteChecked(BUCKET, OBJECT_KEY);
    }

    @Test
    void accountCleanupRollbackPreservesAssetAndLinkWithoutDeletingStorage() {
        Long assetId = createReadyAsset();
        linkService.syncCheerLinks(POST_ID, OWNER_ID, List.of(OBJECT_KEY));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            linkService.unlinkEntity(MediaDomain.CHEER, POST_ID);
            uploadService.deleteOwnedAssetsForAccount(OWNER_ID, java.util.Set.of());
            status.setRollbackOnly();
        });
        assertThat(assets.findById(assetId).orElseThrow().getStatus()).isEqualTo(MediaAssetStatus.READY);
        assertThat(links.existsByAssetId(assetId)).isTrue();
        verify(storage, never()).deleteChecked(anyString(), anyString());
    }

    @Test
    void failedStorageDeletionKeepsNonLinkableTombstoneAndPathsForRetry() {
        Long assetId = createReadyAsset();
        when(storage.deleteChecked(BUCKET, STAGING_KEY))
                .thenReturn(Mono.error(new IllegalStateException("synthetic storage failure")));

        uploadService.cleanupUnlinkedReadyAssets();

        MediaAsset asset = assets.findById(assetId).orElseThrow();
        assertThat(asset.getStatus()).isEqualTo(MediaAssetStatus.DELETED);
        assertThat(asset.getFinalizeToken()).isNotBlank();
        assertThat(asset.getFinalizeLeaseUntil()).isNotNull();
        assertThat(asset.getObjectKey()).isEqualTo(OBJECT_KEY);
        assertThat(asset.getStagingObjectKey()).isEqualTo(STAGING_KEY);
        assertThat(links.existsByAssetId(assetId)).isFalse();
        verify(storage).deleteChecked(BUCKET, STAGING_KEY);

        // A later scheduled tombstone pass can retry the same retained keys.
        when(storage.deleteChecked(BUCKET, STAGING_KEY)).thenReturn(Mono.empty());
        when(storage.deleteChecked(BUCKET, OBJECT_KEY)).thenReturn(Mono.empty());
        when(storage.exists(BUCKET, STAGING_KEY)).thenReturn(Mono.just(false));
        when(storage.exists(BUCKET, OBJECT_KEY)).thenReturn(Mono.just(false));
        when(attemptCleanupService.cleanupForAsset(assetId)).thenReturn(true);
        asset.setFinalizeLeaseUntil(LocalDateTime.now().minusMinutes(1));
        assets.saveAndFlush(asset);
        uploadService.cleanupDeletedFinalizeTombstones();
        MediaAsset retried = assets.findById(assetId).orElseThrow();
        assertThat(retried.getFinalizeCleanupVerifiedAt()).isNotNull();
        assertThat(retried.getObjectKey()).isEqualTo(OBJECT_KEY);
    }

    @Test
    void linkCommittedAfterOrphanSelectionPreventsCheerDeletion() throws Exception {
        Long assetId = createReadyAsset();
        CountDownLatch selected = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        doAnswer(invocation -> {
            var candidates = assets.findUnlinkedAssetsOlderThan(invocation.getArgument(0), invocation.getArgument(1));
            selected.countDown();
            if (!resume.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("selection timeout");
            return candidates;
        }).when(assets).findUnlinkedAssetsOlderThan(any(), any(), any(Pageable.class));
        try (var executor = Executors.newSingleThreadExecutor()) {
            var cleanup = executor.submit(() -> uploadService.cleanupUnlinkedReadyAssets());
            try {
                assertThat(selected.await(5, TimeUnit.SECONDS)).isTrue();
                linkService.syncCheerLinks(POST_ID, OWNER_ID, List.of(OBJECT_KEY));
            } finally {
                resume.countDown();
            }
            cleanup.get(10, TimeUnit.SECONDS);
        }
        assertThat(links.existsByAssetId(assetId)).isTrue();
        assertThat(assets.findById(assetId).orElseThrow().getStatus()).isEqualTo(MediaAssetStatus.READY);
        verify(storage, never()).deleteChecked(anyString(), anyString());
    }

    @Test
    void reportedPostLinkKeepsOwnedAssetDuringAccountCleanup() {
        Long assetId = createReadyAsset();
        linkService.syncCheerLinks(POST_ID, OWNER_ID, List.of(OBJECT_KEY));

        // The post is protected evidence, so its linked asset is skipped and nothing is claimed for deletion.
        int unclaimed = new TransactionTemplate(transactionManager).execute(status ->
                uploadService.deleteOwnedAssetsForAccount(OWNER_ID, java.util.Set.of(POST_ID)));

        assertThat(unclaimed).isZero();
        assertThat(assets.findById(assetId).orElseThrow().getStatus()).isEqualTo(MediaAssetStatus.READY);
        assertThat(links.existsByAssetId(assetId)).isTrue();
        verify(storage, never()).deleteChecked(anyString(), anyString());
    }

    @Test
    void unprotectedPostLinkLetsAccountCleanupClaimTheAsset() {
        // Control for the test above: the same link, with the post not protected, is claimed.
        Long assetId = createReadyAsset();
        linkService.syncCheerLinks(POST_ID, OWNER_ID, List.of(OBJECT_KEY));

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                uploadService.deleteOwnedAssetsForAccount(OWNER_ID, java.util.Set.of()));

        assertThat(assets.findById(assetId).orElseThrow().getStatus()).isEqualTo(MediaAssetStatus.DELETED);
        assertThat(links.existsByAssetId(assetId)).isFalse();
    }

    @Test
    void protectedPostOtherThanTheLinkedPostDoesNotKeepTheAsset() {
        // Negative match: the protected set names a different post, so the asset linked to POST_ID is still claimed.
        Long assetId = createReadyAsset();
        linkService.syncCheerLinks(POST_ID, OWNER_ID, List.of(OBJECT_KEY));

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                uploadService.deleteOwnedAssetsForAccount(OWNER_ID, java.util.Set.of(POST_ID + 1)));

        assertThat(assets.findById(assetId).orElseThrow().getStatus()).isEqualTo(MediaAssetStatus.DELETED);
        assertThat(links.existsByAssetId(assetId)).isFalse();
    }

    @Test
    void protectedParentKeepsItsDerivedChildFromAccountCleanup() {
        // Derived-from branch: the child has no link, but its parent is linked to the protected post.
        Long parentId = createCheerAsset(PARENT_KEY, null);
        Long childId = createCheerAsset(CHILD_KEY, parentId);
        linkService.syncCheerLinks(POST_ID, OWNER_ID, List.of(PARENT_KEY));

        int unclaimed = new TransactionTemplate(transactionManager).execute(status ->
                uploadService.deleteOwnedAssetsForAccount(OWNER_ID, java.util.Set.of(POST_ID)));

        assertThat(unclaimed).isZero();
        assertThat(assets.findById(childId).orElseThrow().getStatus()).isEqualTo(MediaAssetStatus.READY);
        assertThat(assets.findById(parentId).orElseThrow().getStatus()).isEqualTo(MediaAssetStatus.READY);
    }

    @Test
    void protectedChildKeepsItsParentFromAccountCleanup() {
        // Derived-child branch: the parent has no link, but a derivative linked to the protected post keeps it.
        Long parentId = createCheerAsset(PARENT_KEY, null);
        createCheerAsset(CHILD_KEY, parentId);
        linkService.syncCheerLinks(POST_ID, OWNER_ID, List.of(CHILD_KEY));

        int unclaimed = new TransactionTemplate(transactionManager).execute(status ->
                uploadService.deleteOwnedAssetsForAccount(OWNER_ID, java.util.Set.of(POST_ID)));

        assertThat(unclaimed).isZero();
        assertThat(assets.findById(parentId).orElseThrow().getStatus()).isEqualTo(MediaAssetStatus.READY);
    }

    @Test
    void protectedPostLinkOfAnotherDomainDoesNotKeepTheAsset() {
        // Domain check: a link to the protected post id in another domain is not a cheer protection.
        Long assetId = createCheerAsset(OBJECT_KEY, null);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> links.saveAndFlush(MediaAssetLink.builder()
                .assetId(assetId)
                .asset(assets.findById(assetId).orElseThrow())
                .domain(MediaDomain.DIARY)
                .entityId(POST_ID)
                .role(MediaLinkRole.DIARY_PHOTO)
                .build()));

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                uploadService.deleteOwnedAssetsForAccount(OWNER_ID, java.util.Set.of(POST_ID)));

        assertThat(assets.findById(assetId).orElseThrow().getStatus()).isEqualTo(MediaAssetStatus.DELETED);
        assertThat(links.existsByAssetId(assetId)).isFalse();
    }

    @Test
    void committedOrphanClaimRejectsLinkWhileStorageIsStillRunning() throws Exception {
        Long assetId = createReadyAsset();
        CountDownLatch storageStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(storage.deleteChecked(anyString(), anyString())).thenReturn(Mono.fromRunnable(() -> {
            storageStarted.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("storage timeout");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
        }));
        when(storage.exists(anyString(), anyString())).thenReturn(Mono.just(false));
        when(attemptCleanupService.cleanupForAsset(assetId)).thenReturn(true);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var cleanup = executor.submit(() -> uploadService.cleanupUnlinkedReadyAssets());
            try {
                assertThat(storageStarted.await(5, TimeUnit.SECONDS)).isTrue();
                org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        linkService.syncCheerLinks(POST_ID, OWNER_ID, List.of(OBJECT_KEY)))
                        .isInstanceOf(com.example.common.exception.BadRequestBusinessException.class);
            } finally {
                release.countDown();
            }
            cleanup.get(10, TimeUnit.SECONDS);
        }
        assertThat(links.existsByAssetId(assetId)).isFalse();
        assertThat(assets.findById(assetId).orElseThrow().getStatus()).isEqualTo(MediaAssetStatus.DELETED);
    }

    private Long createCheerAsset(String objectKey, Long derivedFromId) {
        return new TransactionTemplate(transactionManager).execute(status -> assets.saveAndFlush(MediaAsset.builder()
                .ownerUserId(OWNER_ID)
                .domain(MediaDomain.CHEER)
                .status(MediaAssetStatus.READY)
                .objectKey(objectKey)
                .stagingObjectKey("media/staging/" + objectKey)
                .declaredContentType("image/webp")
                .declaredBytes(10L)
                .declaredWidth(1)
                .declaredHeight(1)
                .derivedFrom(derivedFromId == null ? null : assets.findById(derivedFromId).orElseThrow())
                .createdAt(LocalDateTime.now().minusDays(2))
                .uploadExpiresAt(LocalDateTime.now().minusDays(1))
                .build()).getId());
    }

    private Long createReadyAsset() {
        return new TransactionTemplate(transactionManager).execute(status -> assets.saveAndFlush(MediaAsset.builder()
                .ownerUserId(OWNER_ID)
                .domain(MediaDomain.CHEER)
                .status(MediaAssetStatus.READY)
                .objectKey(OBJECT_KEY)
                .stagingObjectKey(STAGING_KEY)
                .declaredContentType("image/webp")
                .declaredBytes(10L)
                .declaredWidth(1)
                .declaredHeight(1)
                .createdAt(LocalDateTime.now().minusDays(2))
                .uploadExpiresAt(LocalDateTime.now().minusDays(1))
                .build()).getId());
    }
}
