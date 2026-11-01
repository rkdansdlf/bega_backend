package com.example.media.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.cheerboard.storage.strategy.StorageStrategy;
import com.example.media.entity.MediaAsset;
import com.example.media.entity.MediaAssetStatus;
import com.example.media.entity.MediaDomain;
import com.example.media.entity.MediaFinalizeAttemptStatus;
import com.example.media.repository.MediaAssetRepository;
import com.example.media.repository.MediaFinalizeAttemptRepository;
import com.example.media.repository.MediaAssetLinkRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:media_finalize_claim;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=10000",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
@Import({MediaFinalizeClaimService.class, MediaFinalizeAttemptCleanupService.class, MediaLinkService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MediaFinalizeClaimIntegrationTest {

    @Autowired
    private MediaAssetRepository mediaAssetRepository;

    @Autowired
    private MediaFinalizeAttemptRepository finalizeAttemptRepository;

    @Autowired
    private MediaAssetLinkRepository links;

    @Autowired
    private MediaLinkService mediaLinks;

    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @Autowired
    private MediaFinalizeClaimService finalizeClaimService;

    @Autowired
    private MediaFinalizeAttemptCleanupService attemptCleanupService;

    @MockitoBean
    private StorageStrategy storageStrategy;

    @BeforeEach
    void clearAssets() {
        links.deleteAll();
        finalizeAttemptRepository.deleteAll();
        mediaAssetRepository.deleteAll();
    }

    @Test
    void concurrentFinalizeClaimsAllowExactlyOneWorker() throws Exception {
        MediaAsset asset = mediaAssetRepository.saveAndFlush(pendingAsset());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Optional<MediaFinalizeClaimService.FinalizeClaim>> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return finalizeClaimService.claim(asset.getOwnerUserId(), asset.getId(), "media-profile");
            });
            Future<Optional<MediaFinalizeClaimService.FinalizeClaim>> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return finalizeClaimService.claim(asset.getOwnerUserId(), asset.getId(), "media-profile");
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<Optional<MediaFinalizeClaimService.FinalizeClaim>> claims = List.of(
                    first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));

            assertEquals(1, claims.stream().filter(Optional::isPresent).count());
            assertEquals(MediaAssetStatus.FINALIZING,
                    mediaAssetRepository.findById(asset.getId()).orElseThrow().getStatus());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void accountCleanupAndFinalizeReleaseUseTheSameLockOrder() throws Exception {
        MediaAsset asset = mediaAssetRepository.saveAndFlush(pendingAsset());
        var finalizeClaim = finalizeClaimService.claim(asset.getOwnerUserId(), asset.getId(), "media-profile")
                .orElseThrow();
        CountDownLatch ownerLocked = new CountDownLatch(1);
        CountDownLatch finishAccountClaim = new CountDownLatch(1);
        CountDownLatch releaseStarted = new CountDownLatch(1);
        AtomicLong ownerSession = new AtomicLong();
        AtomicLong releaseSession = new AtomicLong();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var account = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                ownerSession.set(((Number) entityManager.createNativeQuery("SELECT SESSION_ID()").getSingleResult()).longValue());
                mediaAssetRepository.findByIdForUpdate(asset.getId()).orElseThrow();
                ownerLocked.countDown();
                try {
                    if (!finishAccountClaim.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("release did not block");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                return finalizeClaimService.claimOwnedAssetForAccountDeletion(asset, asset.getOwnerUserId());
            }));
            try {
                assertTrue(ownerLocked.await(20, TimeUnit.SECONDS));
                var release = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                    releaseSession.set(((Number) entityManager.createNativeQuery("SELECT SESSION_ID()").getSingleResult()).longValue());
                    releaseStarted.countDown();
                    finalizeClaimService.release(finalizeClaim);
                    return null;
                }));
                assertTrue(releaseStarted.await(20, TimeUnit.SECONDS));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
                Long blocker = null;
                while (System.nanoTime() < deadline) {
                    blocker = jdbc.query("SELECT BLOCKER_ID FROM INFORMATION_SCHEMA.SESSIONS WHERE SESSION_ID = ?",
                            rs -> rs.next() && rs.getObject(1) != null ? ((Number) rs.getObject(1)).longValue() : null,
                            releaseSession.get());
                    if (blocker != null && blocker == ownerSession.get()) break;
                    Thread.sleep(10);
                }
                assertEquals(Long.valueOf(ownerSession.get()), blocker);
                finishAccountClaim.countDown();
                assertTrue(account.get(30, TimeUnit.SECONDS).isPresent());
                release.get(30, TimeUnit.SECONDS);
                assertEquals(MediaAssetStatus.DELETED, mediaAssetRepository.findById(asset.getId()).orElseThrow().getStatus());
            } finally {
                finishAccountClaim.countDown();
            }
        }
    }

    @Test
    void expiredClaimCanBeReplacedAndLateWorkerIsFencedOut() {
        MediaAsset asset = mediaAssetRepository.saveAndFlush(pendingAsset());
        MediaFinalizeClaimService.FinalizeClaim stale = finalizeClaimService
                .claim(asset.getOwnerUserId(), asset.getId(), "media-profile")
                .orElseThrow();
        assertTrue(finalizeClaimService.recordObjectKeys(stale, "attempt/stale.webp", null));
        MediaAsset expiredClaim = mediaAssetRepository.findById(asset.getId()).orElseThrow();
        expiredClaim.setFinalizeLeaseUntil(LocalDateTime.now().minusSeconds(1));
        mediaAssetRepository.saveAndFlush(expiredClaim);

        MediaFinalizeClaimService.FinalizeClaim current = finalizeClaimService
                .claim(asset.getOwnerUserId(), asset.getId(), "media-profile")
                .orElseThrow();
        assertNotEquals(stale.token(), current.token());
        assertEquals("attempt/stale.webp", current.asset().getFinalizeObjectKey());

        MediaFinalizeClaimService.FinalizedObject staleObject = new MediaFinalizeClaimService.FinalizedObject(
                "attempt/stale.webp", "image/webp", 12, 10, 10);
        MediaFinalizeClaimService.FinalizedObject currentObject = new MediaFinalizeClaimService.FinalizedObject(
                "attempt/current.webp", "image/webp", 14, 12, 12);
        assertTrue(finalizeClaimService.complete(
                stale, staleObject, null).isEmpty());

        MediaAsset completed = finalizeClaimService.complete(current, currentObject, null).orElseThrow();

        assertEquals(MediaAssetStatus.READY, completed.getStatus());
        assertEquals("attempt/current.webp", completed.getObjectKey());
        assertNull(completed.getFinalizeToken());
        assertNull(completed.getFinalizeObjectKey());
    }

    @Test
    void profileFinalizeCommitsPrimaryAndFeedDerivativeTogether() {
        MediaAsset asset = mediaAssetRepository.saveAndFlush(pendingAsset());
        MediaFinalizeClaimService.FinalizeClaim claim = finalizeClaimService
                .claim(asset.getOwnerUserId(), asset.getId(), "media-profile")
                .orElseThrow();
        String primaryKey = "media/profile/7/" + asset.getId() + "/primary.webp";
        String feedKey = "media/profile-feed/7/" + asset.getId() + "/feed.webp";
        assertTrue(finalizeClaimService.recordObjectKeys(claim, primaryKey, feedKey));

        MediaAsset completed = finalizeClaimService.complete(
                        claim,
                        new MediaFinalizeClaimService.FinalizedObject(primaryKey, "image/webp", 14, 12, 12),
                        new MediaFinalizeClaimService.FinalizedObject(feedKey, "image/webp", 5, 5, 5))
                .orElseThrow();

        MediaAsset feed = mediaAssetRepository.findByDerivedFrom_Id(asset.getId()).orElseThrow();
        assertEquals(MediaAssetStatus.READY, completed.getStatus());
        assertEquals(primaryKey, completed.getObjectKey());
        assertEquals(MediaAssetStatus.READY, feed.getStatus());
        assertEquals(feedKey, feed.getObjectKey());
        assertEquals(asset.getId(), feed.getDerivedFrom().getId());
        assertNull(completed.getFinalizeToken());
        assertNull(completed.getFinalizeObjectKey());
    }

    @Test
    void accountDeletionClaimFencesFinalizeAndRetainsKeysThroughUploadExpiry() {
        MediaAsset pendingAsset = pendingAsset();
        pendingAsset.setUploadExpiresAt(LocalDateTime.now().plusHours(2));
        MediaAsset asset = mediaAssetRepository.saveAndFlush(pendingAsset);
        MediaFinalizeClaimService.FinalizeClaim worker = finalizeClaimService
                .claim(asset.getOwnerUserId(), asset.getId(), "media-profile")
                .orElseThrow();
        assertTrue(finalizeClaimService.recordObjectKeys(
                worker, "attempt/primary.webp", "attempt/feed.webp"));

        MediaFinalizeClaimService.FinalizeCleanupClaim deletion = finalizeClaimService
                .claimOwnedAssetForAccountDeletion(asset, asset.getOwnerUserId())
                .orElseThrow();
        MediaAsset tombstone = mediaAssetRepository.findById(asset.getId()).orElseThrow();
        assertEquals(MediaAssetStatus.DELETED, tombstone.getStatus());
        assertNull(tombstone.getOwnerUserId());
        assertNull(tombstone.getOriginalFileName());
        assertEquals("media/staging/profile/7/avatar.png", tombstone.getStagingObjectKey());
        assertEquals("attempt/primary.webp", tombstone.getFinalizeObjectKey());
        assertEquals("attempt/feed.webp", tombstone.getFinalizeFeedObjectKey());
        assertTrue(tombstone.getFinalizeLeaseUntil().isAfter(tombstone.getUploadExpiresAt()));

        assertTrue(finalizeClaimService.complete(
                worker,
                new MediaFinalizeClaimService.FinalizedObject("attempt/primary.webp", "image/webp", 10, 10, 10),
                null).isEmpty());
        assertFalse(finalizeClaimService.clearDeletedTombstone(deletion));

        LocalDateTime now = LocalDateTime.now();
        assertEquals(1, mediaAssetRepository.updateDeletedFinalizeVerification(
                asset.getId(), deletion.token(), now.minusMinutes(16), now.minusSeconds(1), now));
        MediaAsset expiredTombstone = mediaAssetRepository.findById(asset.getId()).orElseThrow();
        MediaFinalizeClaimService.FinalizeCleanupClaim cleanup = finalizeClaimService
                .claimDeletedTombstoneForCleanup(expiredTombstone, now)
                .orElseThrow();
        assertEquals("attempt/primary.webp", cleanup.asset().getFinalizeObjectKey());
        assertTrue(finalizeClaimService.clearDeletedTombstone(cleanup));

        MediaAsset cleaned = mediaAssetRepository.findById(asset.getId()).orElseThrow();
        assertEquals(MediaAssetStatus.DELETED, cleaned.getStatus());
        assertNull(cleaned.getFinalizeToken());
        assertNull(cleaned.getFinalizeObjectKey());
        assertNull(cleaned.getObjectKey());
        assertEquals(MediaAsset.CLEARED_STAGING_OBJECT_KEY, cleaned.getStagingObjectKey());
    }

    @Test
    void cheerPurgePendingAssetCannotBeClaimedByAccountOrUploadDeletion() {
        MediaAsset asset = pendingAsset();
        asset.setStatus(MediaAssetStatus.CHEER_PURGE_PENDING);
        asset.setObjectKey("media/cheer/7/synthetic.webp");
        asset = mediaAssetRepository.saveAndFlush(asset);
        Long id = asset.getId();
        Long owner = asset.getOwnerUserId();

        assertTrue(finalizeClaimService.claimOwnedAssetForAccountDeletion(asset, owner).isEmpty());
        assertTrue(finalizeClaimService.claimOwnedAssetForDeletion(asset, owner).isEmpty());
        MediaAsset preserved = mediaAssetRepository.findById(id).orElseThrow();
        assertEquals(MediaAssetStatus.CHEER_PURGE_PENDING, preserved.getStatus());
        assertEquals(owner, preserved.getOwnerUserId());
        assertEquals("media/cheer/7/synthetic.webp", preserved.getObjectKey());
    }

    @Test
    void cheerUploadDeletionCannotClaimAnAlreadyLinkedAsset() {
        MediaAsset asset = pendingAsset();
        asset.setDomain(MediaDomain.CHEER);
        asset.setStatus(MediaAssetStatus.READY);
        asset.setObjectKey("media/cheer/7/linked.webp");
        asset = mediaAssetRepository.saveAndFlush(asset);
        mediaLinks.syncCheerLinks(123L, 7L, List.of(asset.getObjectKey()));

        assertTrue(finalizeClaimService.claimOwnedAssetForDeletion(asset, 7L).isEmpty());
        assertEquals(MediaAssetStatus.READY, mediaAssetRepository.findById(asset.getId()).orElseThrow().getStatus());
        assertTrue(links.existsByAssetId(asset.getId()));
    }

    @Test
    void seatViewUploadDeletionCannotClaimAnAlreadyLinkedAsset() {
        MediaAsset asset = pendingAsset();
        asset.setDomain(MediaDomain.SEAT_VIEW);
        asset.setStatus(MediaAssetStatus.READY);
        asset.setObjectKey("media/seat-view/7/linked.webp");
        asset = mediaAssetRepository.saveAndFlush(asset);
        mediaLinks.linkSeatViewAsset(7L, asset.getObjectKey(), 123L);
        assertTrue(finalizeClaimService.claimOwnedAssetForDeletion(asset, 7L).isEmpty());
        assertEquals(MediaAssetStatus.READY, mediaAssetRepository.findById(asset.getId()).orElseThrow().getStatus());
        assertTrue(links.existsByAssetId(asset.getId()));
    }

    @Test
    void cheerLinkCannotBeCreatedAfterUploadDeletionClaim() {
        MediaAsset asset = pendingAsset();
        asset.setDomain(MediaDomain.CHEER);
        asset.setStatus(MediaAssetStatus.READY);
        asset.setObjectKey("media/cheer/7/deletion-first.webp");
        MediaAsset saved = mediaAssetRepository.saveAndFlush(asset);
        assertTrue(finalizeClaimService.claimOwnedAssetForDeletion(saved, 7L).isPresent());
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                mediaLinks.syncCheerLinks(123L, 7L, List.of(saved.getObjectKey())))
                .isInstanceOf(com.example.common.exception.BadRequestBusinessException.class);
        assertFalse(links.existsByAssetId(saved.getId()));
    }

    @Test
    void uploadDeletionWaitsForCheerLinkCommitBeforeCheckingLink() throws Exception {
        MediaAsset asset = pendingAsset();
        asset.setDomain(MediaDomain.CHEER);
        asset.setStatus(MediaAssetStatus.READY);
        asset.setObjectKey("media/cheer/7/claim-race.webp");
        MediaAsset saved = mediaAssetRepository.saveAndFlush(asset);
        CountDownLatch linked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch claimStarted = new CountDownLatch(1);
        AtomicLong writerSession = new AtomicLong();
        AtomicLong claimSession = new AtomicLong();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var writer = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                writerSession.set(((Number) entityManager.createNativeQuery("SELECT SESSION_ID()").getSingleResult()).longValue());
                mediaLinks.syncCheerLinks(123L, 7L, List.of(saved.getObjectKey()));
                linked.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("release timeout");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                return null;
            }));
            try {
                assertTrue(linked.await(5, TimeUnit.SECONDS));
                var claim = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                    claimSession.set(((Number) entityManager.createNativeQuery("SELECT SESSION_ID()").getSingleResult()).longValue());
                    claimStarted.countDown();
                    return finalizeClaimService.claimOwnedAssetForDeletion(saved, 7L);
                }));
                assertTrue(claimStarted.await(5, TimeUnit.SECONDS));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                Long blocker = null;
                while (System.nanoTime() < deadline) {
                    blocker = jdbc.query("SELECT BLOCKER_ID FROM INFORMATION_SCHEMA.SESSIONS WHERE SESSION_ID = ?",
                            rs -> rs.next() && rs.getObject(1) != null ? ((Number) rs.getObject(1)).longValue() : null,
                            claimSession.get());
                    if (blocker != null && blocker == writerSession.get()) break;
                    Thread.sleep(10);
                }
                assertEquals(Long.valueOf(writerSession.get()), blocker);
                release.countDown();
                writer.get(10, TimeUnit.SECONDS);
                assertTrue(claim.get(10, TimeUnit.SECONDS).isEmpty());
            } finally {
                release.countDown();
            }
        }
        assertEquals(MediaAssetStatus.READY, mediaAssetRepository.findById(saved.getId()).orElseThrow().getStatus());
        assertTrue(links.existsByAssetId(saved.getId()));
    }

    @Test
    void supersededFinalizeAttemptKeepsItsObjectKeysInAppendOnlyLedger() {
        MediaAsset asset = mediaAssetRepository.saveAndFlush(pendingAsset());
        MediaFinalizeClaimService.FinalizeClaim first = finalizeClaimService
                .claim(asset.getOwnerUserId(), asset.getId(), "media-profile")
                .orElseThrow();
        assertTrue(finalizeClaimService.recordObjectKeys(first, "attempt/first.webp", "attempt/first-feed.webp"));

        MediaAsset claimedAsset = mediaAssetRepository.findById(asset.getId()).orElseThrow();
        claimedAsset.setFinalizeLeaseUntil(LocalDateTime.now().minusSeconds(1));
        mediaAssetRepository.saveAndFlush(claimedAsset);

        MediaFinalizeClaimService.FinalizeClaim second = finalizeClaimService
                .claim(asset.getOwnerUserId(), asset.getId(), "media-profile")
                .orElseThrow();

        var priorAttempt = finalizeAttemptRepository.findById(first.token()).orElseThrow();
        var currentAttempt = finalizeAttemptRepository.findById(second.token()).orElseThrow();
        assertEquals("attempt/first.webp", priorAttempt.getObjectKey());
        assertEquals("attempt/first-feed.webp", priorAttempt.getFeedObjectKey());
        assertEquals(MediaFinalizeAttemptStatus.CLEANUP_REQUIRED, priorAttempt.getStatus());
        assertEquals(MediaFinalizeAttemptStatus.ACTIVE, currentAttempt.getStatus());
        assertNotEquals(first.token(), second.token());
    }

    @Test
    void delayedPutIsReconciledByRecurringCleanupWithoutLosingAttemptKey() {
        MediaAsset asset = mediaAssetRepository.saveAndFlush(pendingAsset());
        MediaFinalizeClaimService.FinalizeClaim attempt = finalizeClaimService
                .claim(asset.getOwnerUserId(), asset.getId(), "media-profile")
                .orElseThrow();
        String objectKey = "attempt/delayed.webp";
        assertTrue(finalizeClaimService.recordObjectKeys(attempt, objectKey, null));
        assertTrue(finalizeClaimService.markAttemptCleanupRequired(attempt));

        Set<String> objects = new HashSet<>();
        when(storageStrategy.deleteChecked("media-profile", objectKey))
                .thenAnswer(invocation -> Mono.fromRunnable(() -> objects.remove(objectKey)));
        when(storageStrategy.exists("media-profile", objectKey))
                .thenAnswer(invocation -> Mono.just(objects.contains(objectKey)));

        LocalDateTime firstCleanupAt = LocalDateTime.now().plusSeconds(2);
        assertEquals(1, attemptCleanupService.cleanupDueAttempts(firstCleanupAt));
        var cleanedAttempt = finalizeAttemptRepository.findById(attempt.token()).orElseThrow();
        assertEquals(MediaFinalizeAttemptStatus.CLEANED, cleanedAttempt.getStatus());
        assertEquals(objectKey, cleanedAttempt.getObjectKey());

        objects.add(objectKey);
        assertEquals(1, attemptCleanupService.cleanupDueAttempts(firstCleanupAt.plusDays(1)));
        assertFalse(objects.contains(objectKey));
        assertEquals(objectKey,
                finalizeAttemptRepository.findById(attempt.token()).orElseThrow().getObjectKey());
    }

    @Test
    void expiredFinalizeCleanupOrderingRotatesPastRecentlyRetriedAssets() {
        LocalDateTime now = LocalDateTime.now();
        MediaAsset recentlyRetried = pendingAsset();
        recentlyRetried.setStatus(MediaAssetStatus.FINALIZING);
        recentlyRetried.setFinalizeToken("retry-token");
        recentlyRetried.setFinalizeLeaseUntil(now.plusMinutes(15));
        recentlyRetried.setUploadExpiresAt(now.minusDays(4));
        MediaAsset waiting = pendingAsset();
        waiting.setStatus(MediaAssetStatus.FINALIZING);
        waiting.setFinalizeToken("waiting-token");
        waiting.setFinalizeLeaseUntil(now.minusHours(1));
        waiting.setUploadExpiresAt(now.minusDays(3));
        mediaAssetRepository.saveAllAndFlush(List.of(recentlyRetried, waiting));

        List<MediaAsset> due = mediaAssetRepository
                .findByStatusAndFinalizeLeaseUntilBeforeAndUploadExpiresAtBeforeOrderByFinalizeLeaseUntilAscUploadExpiresAtAscIdAsc(
                        MediaAssetStatus.FINALIZING,
                        now.plusMinutes(16),
                        now.minusDays(1),
                        PageRequest.of(0, 1));

        assertEquals(1, due.size());
        assertEquals(waiting.getId(), due.getFirst().getId());
    }

    @Test
    void readyStagingKeyCannotBeSelectedOrClearedBeforeUploadQuiescence() {
        LocalDateTime now = LocalDateTime.now();
        MediaAsset asset = pendingAsset();
        asset.setStatus(MediaAssetStatus.READY);
        asset.setObjectKey("media/profile/7/ready.webp");
        asset.setUploadExpiresAt(now.minusMinutes(14));
        MediaAsset ready = mediaAssetRepository.saveAndFlush(asset);

        List<MediaAsset> tooEarly = mediaAssetRepository
                .findByStatusAndStagingObjectKeyIsNotNullAndStagingObjectKeyNotAndUploadExpiresAtBeforeAndIdGreaterThanOrderByIdAsc(
                        MediaAssetStatus.READY,
                        MediaAsset.CLEARED_STAGING_OBJECT_KEY,
                        now.minusMinutes(15),
                        0L,
                        PageRequest.of(0, 10));
        int clearedTooEarly = mediaAssetRepository.clearReadyStagingObjectKey(
                ready.getId(),
                ready.getStagingObjectKey(),
                MediaAsset.CLEARED_STAGING_OBJECT_KEY,
                now,
                now.minusMinutes(15));

        assertTrue(tooEarly.isEmpty());
        assertEquals(0, clearedTooEarly);

        LocalDateTime afterGrace = now.plusMinutes(2);
        List<MediaAsset> eligible = mediaAssetRepository
                .findByStatusAndStagingObjectKeyIsNotNullAndStagingObjectKeyNotAndUploadExpiresAtBeforeAndIdGreaterThanOrderByIdAsc(
                        MediaAssetStatus.READY,
                        MediaAsset.CLEARED_STAGING_OBJECT_KEY,
                        afterGrace.minusMinutes(15),
                        0L,
                        PageRequest.of(0, 10));

        assertEquals(List.of(ready.getId()), eligible.stream().map(MediaAsset::getId).toList());
        assertEquals(1, mediaAssetRepository.clearReadyStagingObjectKey(
                ready.getId(),
                ready.getStagingObjectKey(),
                MediaAsset.CLEARED_STAGING_OBJECT_KEY,
                afterGrace,
                afterGrace.minusMinutes(15)));
        assertEquals(MediaAsset.CLEARED_STAGING_OBJECT_KEY,
                mediaAssetRepository.findById(ready.getId()).orElseThrow().getStagingObjectKey());
    }

    @Test
    void readyStagingClearRejectsRowsChangedAfterCleanupSelection() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime eligibleBefore = now.minusMinutes(15);

        MediaAsset keyChanged = readyAsset(now.minusHours(1));
        MediaAsset statusChanged = readyAsset(now.minusHours(1));
        MediaAsset expiryExtended = readyAsset(now.minusHours(1));
        mediaAssetRepository.saveAllAndFlush(List.of(keyChanged, statusChanged, expiryExtended));
        String expectedKey = keyChanged.getStagingObjectKey();
        String expectedExpiryKey = expiryExtended.getStagingObjectKey();
        keyChanged.setStagingObjectKey("media/staging/profile/7/replaced.png");
        statusChanged.setStatus(MediaAssetStatus.DELETED);
        expiryExtended.setUploadExpiresAt(now.plusHours(1));
        mediaAssetRepository.saveAllAndFlush(List.of(keyChanged, statusChanged, expiryExtended));

        assertEquals(0, mediaAssetRepository.clearReadyStagingObjectKey(
                keyChanged.getId(), expectedKey, MediaAsset.CLEARED_STAGING_OBJECT_KEY, now, eligibleBefore));
        assertEquals(0, mediaAssetRepository.clearReadyStagingObjectKey(
                statusChanged.getId(),
                statusChanged.getStagingObjectKey(),
                MediaAsset.CLEARED_STAGING_OBJECT_KEY,
                now,
                eligibleBefore));
        assertEquals(0, mediaAssetRepository.clearReadyStagingObjectKey(
                expiryExtended.getId(),
                expiryExtended.getStagingObjectKey(),
                MediaAsset.CLEARED_STAGING_OBJECT_KEY,
                now,
                eligibleBefore));

        assertEquals("media/staging/profile/7/replaced.png",
                mediaAssetRepository.findById(keyChanged.getId()).orElseThrow().getStagingObjectKey());
        assertEquals(MediaAssetStatus.DELETED,
                mediaAssetRepository.findById(statusChanged.getId()).orElseThrow().getStatus());
        assertEquals(expectedExpiryKey,
                mediaAssetRepository.findById(expiryExtended.getId()).orElseThrow().getStagingObjectKey());
    }

    private MediaAsset readyAsset(LocalDateTime uploadExpiresAt) {
        return MediaAsset.builder()
                .ownerUserId(7L)
                .domain(MediaDomain.PROFILE)
                .status(MediaAssetStatus.READY)
                .originalFileName("avatar.png")
                .declaredContentType("image/png")
                .declaredBytes(64L)
                .declaredWidth(16)
                .declaredHeight(16)
                .stagingObjectKey("media/staging/profile/7/pending.png")
                .objectKey("media/profile/7/ready.webp")
                .uploadExpiresAt(uploadExpiresAt)
                .build();
    }

    @Test
    void failedAttemptCleanupIsRetryableWithBackoff() {
        MediaAsset asset = mediaAssetRepository.saveAndFlush(pendingAsset());
        MediaFinalizeClaimService.FinalizeClaim attempt = finalizeClaimService
                .claim(asset.getOwnerUserId(), asset.getId(), "media-profile")
                .orElseThrow();
        String objectKey = "attempt/retry.webp";
        assertTrue(finalizeClaimService.recordObjectKeys(attempt, objectKey, null));
        assertTrue(finalizeClaimService.markAttemptCleanupRequired(attempt));
        when(storageStrategy.deleteChecked("media-profile", objectKey))
                .thenReturn(Mono.error(new IllegalStateException("temporary storage failure")));

        LocalDateTime cleanupAt = LocalDateTime.now().plusSeconds(2);
        assertEquals(1, attemptCleanupService.cleanupDueAttempts(cleanupAt));

        var retriedAttempt = finalizeAttemptRepository.findById(attempt.token()).orElseThrow();
        assertEquals(MediaFinalizeAttemptStatus.CLEANUP_REQUIRED, retriedAttempt.getStatus());
        assertEquals(1, retriedAttempt.getCleanupAttemptCount());
        assertTrue(retriedAttempt.getNextCleanupAt().isAfter(cleanupAt.plusMinutes(14)));
        assertEquals(0, attemptCleanupService.cleanupDueAttempts(cleanupAt));
        verify(storageStrategy).deleteChecked("media-profile", objectKey);
    }

    @Test
    void concurrentAttemptCleanupClaimsAllowExactlyOneWorker() throws Exception {
        MediaAsset asset = mediaAssetRepository.saveAndFlush(pendingAsset());
        MediaFinalizeClaimService.FinalizeClaim attempt = finalizeClaimService
                .claim(asset.getOwnerUserId(), asset.getId(), "media-profile")
                .orElseThrow();
        assertTrue(finalizeClaimService.recordObjectKeys(attempt, "attempt/concurrent.webp", null));
        assertTrue(finalizeClaimService.markAttemptCleanupRequired(attempt));

        LocalDateTime now = LocalDateTime.now().plusSeconds(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Optional<MediaFinalizeClaimService.FinalizeAttemptCleanupClaim>> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return finalizeClaimService.claimDueAttemptCleanup(attempt.token(), now);
            });
            Future<Optional<MediaFinalizeClaimService.FinalizeAttemptCleanupClaim>> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return finalizeClaimService.claimDueAttemptCleanup(attempt.token(), now);
            });

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<Optional<MediaFinalizeClaimService.FinalizeAttemptCleanupClaim>> claims = List.of(
                    first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));

            assertEquals(1, claims.stream().filter(Optional::isPresent).count());
            assertEquals(MediaFinalizeAttemptStatus.CLEANUP_PROCESSING,
                    finalizeAttemptRepository.findById(attempt.token()).orElseThrow().getStatus());
        } finally {
            executor.shutdownNow();
        }
    }

    private MediaAsset pendingAsset() {
        return MediaAsset.builder()
                .ownerUserId(7L)
                .domain(MediaDomain.PROFILE)
                .status(MediaAssetStatus.PENDING)
                .originalFileName("avatar.png")
                .declaredContentType("image/png")
                .declaredBytes(64L)
                .declaredWidth(16)
                .declaredHeight(16)
                .stagingObjectKey("media/staging/profile/7/avatar.png")
                .uploadExpiresAt(LocalDateTime.now().plusDays(1))
                .build();
    }
}
