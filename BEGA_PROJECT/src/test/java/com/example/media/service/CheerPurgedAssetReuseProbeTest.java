package com.example.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.AccountDeletionContentService;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.service.CheerPostService;
import com.example.cheerboard.service.CheerPostTransactionBoundary;
import com.example.cheerboard.storage.entity.PostImage;
import com.example.cheerboard.storage.repository.PostImageRepository;
import com.example.cheerboard.storage.service.ImageService;
import com.example.kbo.entity.TeamEntity;
import com.example.kbo.repository.TeamRepository;
import com.example.media.entity.MediaAsset;
import com.example.media.entity.MediaAssetStatus;
import com.example.media.entity.MediaDomain;
import com.example.media.repository.MediaAssetLinkRepository;
import com.example.media.repository.MediaAssetRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({MediaLinkService.class, CheerPostTransactionBoundary.class, CheerMediaPurgeReservation.class,
        AccountDeletionContentService.class, CheerIndividualMediaDeletion.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CheerPurgedAssetReuseProbeTest {
    @Autowired MediaAssetRepository assets;
    @Autowired MediaAssetLinkRepository links;
    @Autowired MediaLinkService mediaLinks;
    @Autowired CheerPostRepo posts;
    @Autowired UserRepository users;
    @Autowired TeamRepository teams;
    @Autowired PostImageRepository images;
    @Autowired CheerPostTransactionBoundary boundary;
    @Autowired CheerMediaPurgeReservation reservation;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountDeletionContentService contentService;
    @Autowired CheerIndividualMediaDeletion individualMediaDeletion;
    @MockitoBean MediaUploadService mediaUploadService;
    @MockitoBean ImageService imageService;
    @MockitoBean com.example.cheerboard.storage.config.StorageConfig storageConfig;

    @Test
    void individuallyDeletedManagedImageCannotBeLinkedAgain() {
        String key = "media/cheer/synthetic/individual.webp";
        Long[] ids = new TransactionTemplate(transactionManager).execute(status -> {
            TeamEntity team = teams.save(TeamEntity.builder().teamId("ZI").teamName("Synthetic")
                    .teamShortName("ZI").city("Synthetic").build());
            UserEntity user = users.saveAndFlush(UserEntity.builder().uniqueId(UUID.randomUUID())
                    .handle("individualprobe").name("Synthetic").email("individual@example.invalid")
                    .role("ROLE_USER").enabled(true).build());
            CheerPost post = posts.saveAndFlush(CheerPost.builder().author(user).team(team).content("synthetic").build());
            MediaAsset asset = assets.saveAndFlush(MediaAsset.builder()
                    .ownerUserId(user.getId()).domain(MediaDomain.CHEER).status(MediaAssetStatus.READY)
                    .declaredContentType("image/webp").declaredBytes(10L).declaredWidth(1).declaredHeight(1)
                    .stagingObjectKey("synthetic-individual-stage").objectKey(key)
                    .uploadExpiresAt(LocalDateTime.now().plusHours(1)).build());
            PostImage image = images.saveAndFlush(PostImage.builder().post(post).storagePath(key)
                    .mimeType("image/webp").bytes(10L).build());
            mediaLinks.syncCheerLinks(post.getId(), user.getId(), List.of(key));
            return new Long[] {post.getId(), user.getId(), asset.getId(), image.getId()};
        });
        var currentUser = mock(com.example.cheerboard.config.CurrentUser.class);
        when(currentUser.get()).thenReturn(users.findById(ids[1]).orElseThrow());
        var storage = mock(com.example.cheerboard.storage.strategy.StorageStrategy.class);
        var config = mock(com.example.cheerboard.storage.config.StorageConfig.class);
        when(config.getCheerBucket()).thenReturn("synthetic-bucket");
        when(storageConfig.getCheerBucket()).thenReturn("synthetic-bucket");
        when(storage.deleteChecked("synthetic-bucket", key)).thenReturn(reactor.core.publisher.Mono.empty());
        var imageService = new ImageService(images, posts, storage,
                mock(com.example.cheerboard.storage.validator.ImageValidator.class), config, currentUser,
                new com.example.cheerboard.service.PermissionValidator(), mock(org.springframework.cache.CacheManager.class),
                mock(org.springframework.data.redis.core.StringRedisTemplate.class),
                mock(com.example.common.image.ImageUtil.class),
                mock(com.example.common.image.ImageOptimizationMetricsService.class),
                mock(com.example.auth.service.PublicVisibilityVerifier.class),
                mock(MediaObjectKeyGuard.class), individualMediaDeletion, transactionManager, assets, links);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(users.findById(ids[1]).orElseThrow().isPendingDeletion()).isFalse();
            try (var executor = Executors.newSingleThreadExecutor()) {
                executor.submit(() -> jdbc.update(
                        "UPDATE users SET pending_deletion = ?, deletion_scheduled_for = ? WHERE id = ?",
                        true, LocalDateTime.now().plusDays(1), ids[1])).get(10, TimeUnit.SECONDS);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            assertThatThrownBy(() -> imageService.deleteImage(ids[3]))
                    .isInstanceOf(com.example.common.exception.ConflictBusinessException.class);
            status.setRollbackOnly();
        });
        jdbc.update("UPDATE users SET pending_deletion = ?, deletion_scheduled_for = NULL WHERE id = ?", false, ids[1]);

        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);
        readOnly.executeWithoutResult(status -> {
            assertThatThrownBy(() -> imageService.deleteImage(ids[3])).isInstanceOf(IllegalStateException.class);
            status.setRollbackOnly();
        });
        assertThat(images.findById(ids[3])).isPresent();

        jdbc.update("INSERT INTO seat_view_photo (user_id, storage_path, source_type, user_selected, reward_granted, createdat, updatedat) VALUES (?, ?, ?, ?, ?, ?, ?)",
                ids[1], "synthetic-bucket/" + key, "SEATMAP_UPLOAD", false, false, LocalDateTime.now(), LocalDateTime.now());
        assertThatThrownBy(() -> imageService.deleteImage(ids[3]))
                .isInstanceOf(com.example.common.exception.ConflictBusinessException.class);
        assertThat(images.findById(ids[3])).isPresent();
        assertThat(assets.findById(ids[2]).orElseThrow().getStatus()).isEqualTo(MediaAssetStatus.READY);
        jdbc.update("DELETE FROM seat_view_photo WHERE storage_path = ?", "synthetic-bucket/" + key);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            imageService.deleteImage(ids[3]);
            status.setRollbackOnly();
        });
        assertThat(assets.findById(ids[2]).orElseThrow().getStatus()).isEqualTo(MediaAssetStatus.READY);
        assertThat(links.existsByAssetId(ids[2])).isTrue();
        assertThat(images.findById(ids[3])).isPresent();
        org.mockito.Mockito.verify(storage, org.mockito.Mockito.never())
                .deleteChecked(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());

        imageService.deleteImage(ids[3]);

        MediaAsset tombstone = assets.findById(ids[2]).orElseThrow();
        assertThat(tombstone.getStatus()).isEqualTo(MediaAssetStatus.DELETED);
        assertThat(tombstone.getFinalizeToken()).isNotBlank();
        assertThat(tombstone.getFinalizeLeaseUntil()).isAfter(tombstone.getUploadExpiresAt());
        assertThat(tombstone.getObjectKey()).isEqualTo(key);
        assertThat(images.findById(ids[3])).isEmpty();
        assertThat(links.existsByAssetId(ids[2])).isFalse();
        org.mockito.Mockito.verify(storage, org.mockito.Mockito.never()).deleteChecked(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());

        assertThatThrownBy(() -> mediaLinks.syncCheerLinks(ids[0], ids[1], List.of(key)))
                .as("a removed storage object must not be linked again")
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void purgedImageMustNotBeLinkedToAnotherPost() {
        String key = "media/cheer/17/synthetic.webp";
        Long[] ids = new TransactionTemplate(transactionManager).execute(status -> {
            TeamEntity team = teams.save(TeamEntity.builder().teamId("ZZ").teamName("Synthetic")
                    .teamShortName("ZZ").city("Synthetic").build());
            UserEntity user = users.saveAndFlush(UserEntity.builder().uniqueId(UUID.randomUUID())
                    .handle("probe").name("Synthetic").email("probe@example.invalid")
                    .role("ROLE_USER").enabled(true).build());
            CheerPost post = posts.saveAndFlush(CheerPost.builder().author(user).team(team).content("synthetic").build());
            MediaAsset asset = assets.saveAndFlush(MediaAsset.builder()
                    .ownerUserId(user.getId()).domain(MediaDomain.CHEER).status(MediaAssetStatus.READY)
                    .declaredContentType("image/webp").declaredBytes(10L).declaredWidth(1).declaredHeight(1)
                    .stagingObjectKey("synthetic-staging").objectKey(key)
                    .uploadExpiresAt(LocalDateTime.now().plusHours(1)).build());
            images.saveAndFlush(PostImage.builder().post(post).storagePath(key).mimeType("image/webp").bytes(10L).build());
            mediaLinks.syncCheerLinks(post.getId(), user.getId(), List.of(key));
            jdbc.update("UPDATE cheer_post SET deleted = ? WHERE id = ?", true, post.getId());
            return new Long[] {post.getId(), user.getId(), asset.getId()};
        });
        ImageService imageService = mock(ImageService.class);
        org.mockito.Mockito.doReturn(true).when(imageService).deleteImagesByPostId(ids[0]);
        assertThat(images.findByPostIdOrderByCreatedAtAsc(ids[0])).hasSize(1);
        CheerPostService service = new CheerPostService(posts, null, null, users, null, imageService, images,
                null, null, null, null, null, null, null, entityManager, mediaLinks, null, null, boundary, reservation);

        assertThat(service.cleanupSoftDeletedPost(ids[0])).isTrue();
        assertThat(links.findById(ids[2])).isEmpty();
        MediaAsset retained = assets.findById(ids[2]).orElseThrow();
        assertThat(retained.getStatus()).isEqualTo(MediaAssetStatus.DELETED);
        assertThat(retained.getObjectKey()).isEqualTo(key);
        assertThat(retained.getFinalizeToken()).isNotBlank();
        assertThatThrownBy(() -> mediaLinks.syncCheerLinks(999L, ids[1], List.of(key)))
                .as("a deleted storage object must not be accepted as READY")
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void crossOwnerAccountContentWaitsForPostPurgeStorage() throws Exception {
        Long[] ids = new TransactionTemplate(transactionManager).execute(status -> {
            TeamEntity team = teams.save(TeamEntity.builder().teamId("ZY").teamName("Synthetic")
                    .teamShortName("ZY").city("Synthetic").build());
            UserEntity author = users.saveAndFlush(UserEntity.builder().uniqueId(UUID.randomUUID())
                    .handle("purgeauthor").name("Synthetic").email("purgeauthor@example.invalid")
                    .role("ROLE_USER").enabled(true).build());
            UserEntity imageOwner = users.saveAndFlush(UserEntity.builder().uniqueId(UUID.randomUUID())
                    .handle("imageowner").name("Synthetic").email("imageowner@example.invalid")
                    .role("ROLE_USER").enabled(true).build());
            CheerPost post = posts.saveAndFlush(CheerPost.builder().author(author).team(team).content("synthetic").build());
            String key = "media/cheer/" + imageOwner.getId() + "/cross-owner.webp";
            MediaAsset asset = assets.saveAndFlush(MediaAsset.builder()
                    .ownerUserId(imageOwner.getId()).domain(MediaDomain.CHEER).status(MediaAssetStatus.READY)
                    .declaredContentType("image/webp").declaredBytes(10L).declaredWidth(1).declaredHeight(1)
                    .stagingObjectKey("synthetic-cross-stage").objectKey(key)
                    .uploadExpiresAt(LocalDateTime.now().plusHours(1)).build());
            images.saveAndFlush(PostImage.builder().post(post).storagePath(key).mimeType("image/webp").bytes(10L).build());
            mediaLinks.syncCheerLinks(post.getId(), imageOwner.getId(), List.of(key));
            jdbc.update("UPDATE cheer_post SET deleted = ? WHERE id = ?", true, post.getId());
            return new Long[] {post.getId(), imageOwner.getId(), asset.getId()};
        });
        when(mediaUploadService.hasPendingCheerPurge(ids[1])).thenAnswer(invocation ->
                assets.existsByOwnerUserIdAndStatus(ids[1], MediaAssetStatus.CHEER_PURGE_PENDING));
        ImageService imageService = mock(ImageService.class);
        CountDownLatch storageStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(imageService.deleteImagesByPostId(ids[0])).thenAnswer(invocation -> {
            storageStarted.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("storage timeout");
            return false;
        });
        CheerPostService service = new CheerPostService(posts, null, null, users, null, imageService, images,
                null, null, null, null, null, null, null, entityManager, mediaLinks, null, null, boundary, reservation);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var cleanup = executor.submit(() -> service.cleanupSoftDeletedPost(ids[0]));
            try {
                assertThat(storageStarted.await(5, TimeUnit.SECONDS)).isTrue();
                AccountDeletionContentService.Result result = new TransactionTemplate(transactionManager).execute(status -> {
                    users.findByIdForWrite(ids[1]).orElseThrow();
                    return contentService.anonymize(ids[1]);
                });
                assertThat(result).isEqualTo(AccountDeletionContentService.Result.PURGE_PENDING);
                assertThat(assets.findById(ids[2]).orElseThrow().getStatus())
                        .isEqualTo(MediaAssetStatus.CHEER_PURGE_PENDING);
            } finally {
                release.countDown();
            }
            assertThat(cleanup.get(10, TimeUnit.SECONDS)).isFalse();
        }
        assertThat(assets.findById(ids[2]).orElseThrow().getObjectKey()).endsWith("cross-owner.webp");
        jdbc.update("UPDATE cheer_post SET purge_retry_at = ? WHERE id = ?",
                LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(1), ids[0]);
        org.mockito.Mockito.doReturn(true).when(imageService).deleteImagesByPostId(ids[0]);
        assertThat(service.cleanupSoftDeletedPost(ids[0])).isTrue();
        MediaAsset tombstone = assets.findById(ids[2]).orElseThrow();
        assertThat(tombstone.getStatus()).isEqualTo(MediaAssetStatus.DELETED);
        assertThat(tombstone.getFinalizeToken()).isNotBlank();
        assertThat(tombstone.getObjectKey()).endsWith("cross-owner.webp");
        assertThat(links.existsByAssetId(ids[2])).isFalse();
    }

    @Test
    void postPurgeWaitsForCrossOwnerAccountLockBeforeStorage() throws Exception {
        Long[] ids = new TransactionTemplate(transactionManager).execute(status -> {
            TeamEntity team = teams.save(TeamEntity.builder().teamId("ZX").teamName("Synthetic")
                    .teamShortName("ZX").city("Synthetic").build());
            UserEntity imageOwner = users.saveAndFlush(UserEntity.builder().uniqueId(UUID.randomUUID())
                    .handle("lockedowner").name("Synthetic").email("lockedowner@example.invalid")
                    .role("ROLE_USER").enabled(true).build());
            UserEntity author = users.saveAndFlush(UserEntity.builder().uniqueId(UUID.randomUUID())
                    .handle("lockedauthor").name("Synthetic").email("lockedauthor@example.invalid")
                    .role("ROLE_USER").enabled(true).build());
            CheerPost post = posts.saveAndFlush(CheerPost.builder().author(author).team(team).content("synthetic").build());
            String key = "media/cheer/" + imageOwner.getId() + "/owner-lock.webp";
            MediaAsset asset = assets.saveAndFlush(MediaAsset.builder()
                    .ownerUserId(imageOwner.getId()).domain(MediaDomain.CHEER).status(MediaAssetStatus.READY)
                    .declaredContentType("image/webp").declaredBytes(10L).declaredWidth(1).declaredHeight(1)
                    .stagingObjectKey("synthetic-owner-lock-stage").objectKey(key)
                    .uploadExpiresAt(LocalDateTime.now().plusHours(1)).build());
            images.saveAndFlush(PostImage.builder().post(post).storagePath(key).mimeType("image/webp").bytes(10L).build());
            mediaLinks.syncCheerLinks(post.getId(), imageOwner.getId(), List.of(key));
            jdbc.update("UPDATE cheer_post SET deleted = ? WHERE id = ?", true, post.getId());
            return new Long[] {post.getId(), imageOwner.getId(), asset.getId()};
        });
        ImageService imageService = mock(ImageService.class);
        when(imageService.deleteImagesByPostId(ids[0])).thenReturn(false);
        CheerPostService service = new CheerPostService(posts, null, null, users, null, imageService, images,
                null, null, null, null, null, null, null, entityManager, mediaLinks, null, null, boundary, reservation);
        CountDownLatch ownerLocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var ownerSession = new java.util.concurrent.atomic.AtomicLong();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var account = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                ownerSession.set(((Number) entityManager.createNativeQuery("SELECT SESSION_ID()").getSingleResult()).longValue());
                users.findByIdForWrite(ids[1]).orElseThrow();
                ownerLocked.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("release timeout");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                return null;
            }));
            try {
                assertThat(ownerLocked.await(5, TimeUnit.SECONDS)).isTrue();
                var cleanup = executor.submit(() -> service.cleanupSoftDeletedPost(ids[0]));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                int blocked = 0;
                while (System.nanoTime() < deadline) {
                    blocked = jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE BLOCKER_ID = ?",
                            Integer.class, ownerSession.get());
                    if (blocked > 0) break;
                    Thread.sleep(10);
                }
                assertThat(blocked).isPositive();
                org.mockito.Mockito.verify(imageService, org.mockito.Mockito.never()).deleteImagesByPostId(ids[0]);
                release.countDown();
                account.get(10, TimeUnit.SECONDS);
                assertThat(cleanup.get(10, TimeUnit.SECONDS)).isFalse();
            } finally {
                release.countDown();
            }
        }
        assertThat(assets.findById(ids[2]).orElseThrow().getStatus()).isEqualTo(MediaAssetStatus.CHEER_PURGE_PENDING);
    }

    @Test
    void postPurgeRetainsTombstoneForLinkLeftByIndividualImageDeletion() {
        Long[] ids = new TransactionTemplate(transactionManager).execute(status -> {
            TeamEntity team = teams.save(TeamEntity.builder().teamId("ZW").teamName("Synthetic")
                    .teamShortName("ZW").city("Synthetic").build());
            UserEntity owner = users.saveAndFlush(UserEntity.builder().uniqueId(UUID.randomUUID())
                    .handle("staleowner").name("Synthetic").email("staleowner@example.invalid")
                    .role("ROLE_USER").enabled(true).build());
            CheerPost post = posts.saveAndFlush(CheerPost.builder().author(owner).team(team).content("synthetic").build());
            String key = "media/cheer/" + owner.getId() + "/removed-image.webp";
            MediaAsset asset = assets.saveAndFlush(MediaAsset.builder()
                    .ownerUserId(owner.getId()).domain(MediaDomain.CHEER).status(MediaAssetStatus.READY)
                    .declaredContentType("image/webp").declaredBytes(10L).declaredWidth(1).declaredHeight(1)
                    .stagingObjectKey("synthetic-removed-stage").objectKey(key)
                    .uploadExpiresAt(LocalDateTime.now().plusHours(1)).build());
            mediaLinks.syncCheerLinks(post.getId(), owner.getId(), List.of(key));
            jdbc.update("UPDATE cheer_post SET deleted = ? WHERE id = ?", true, post.getId());
            return new Long[] {post.getId(), asset.getId()};
        });
        ImageService imageService = mock(ImageService.class);
        when(imageService.deleteImagesByPostId(ids[0])).thenReturn(true);
        CheerPostService service = new CheerPostService(posts, null, null, users, null, imageService, images,
                null, null, null, null, null, null, null, entityManager, mediaLinks, null, null, boundary, reservation);

        assertThat(service.cleanupSoftDeletedPost(ids[0])).isTrue();
        MediaAsset tombstone = assets.findById(ids[1]).orElseThrow();
        assertThat(tombstone.getStatus()).isEqualTo(MediaAssetStatus.DELETED);
        assertThat(tombstone.getObjectKey()).endsWith("removed-image.webp");
        assertThat(tombstone.getFinalizeToken()).isNotBlank();
        assertThat(links.existsByAssetId(ids[1])).isFalse();
    }
}
