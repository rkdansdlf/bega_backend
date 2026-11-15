package com.example.cheerboard.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.kbo.entity.TeamEntity;
import com.example.kbo.repository.TeamRepository;
import com.example.media.entity.MediaAsset;
import com.example.media.entity.MediaAssetStatus;
import com.example.media.entity.MediaDomain;
import com.example.media.repository.MediaAssetRepository;
import com.example.media.service.CheerMediaPurgeReservation;
import com.example.media.service.MediaLinkService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import({CheerMediaPurgeReservation.class, MediaLinkService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CheerPurgeClaimIntegrationTest {
    @Autowired CheerPostRepo posts;
    @Autowired UserRepository users;
    @Autowired TeamRepository teams;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MediaAssetRepository assets;
    @Autowired MediaLinkService mediaLinks;
    @Autowired CheerMediaPurgeReservation reservation;

    private Long newDeletedPost() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            TeamEntity team = teams.findById("ZZ").orElseGet(() -> teams.save(TeamEntity.builder()
                    .teamId("ZZ").teamName("Synthetic").teamShortName("ZZ").city("Synthetic").build()));
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            UserEntity author = users.saveAndFlush(UserEntity.builder().uniqueId(UUID.randomUUID())
                    .handle("u" + suffix).name("Synthetic").email(suffix + "@example.invalid")
                    .role("ROLE_USER").enabled(true).build());
            CheerPost post = posts.saveAndFlush(CheerPost.builder().author(author).team(team)
                    .postType(PostType.NORMAL).content("synthetic").build());
            jdbc.update("UPDATE cheer_post SET deleted = ? WHERE id = ?", true, post.getId());
            return post.getId();
        });
    }

    @Test
    void competingClaimsHaveOneWinnerAndStaleTokenCannotFinalize() throws Exception {
        Long id = newDeletedPost();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> { start.await(); return new TransactionTemplate(transactionManager)
                    .execute(s -> posts.claimPurge(id, "first", now, now.plusMinutes(15))); });
            var second = executor.submit(() -> { start.await(); return new TransactionTemplate(transactionManager)
                    .execute(s -> posts.claimPurge(id, "second", now, now.plusMinutes(15))); });
            start.countDown();
            assertThat(first.get(30, TimeUnit.SECONDS) + second.get(30, TimeUnit.SECONDS)).isEqualTo(1);
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                CheerPost locked = posts.findIncludingDeletedByIdForUpdate(id).orElseThrow();
                assertThat(locked.getPurgeStartedAt()).isNotNull();
                assertThat(posts.lockClaimedPurge(id, "stale")).isEmpty();
            });
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void releasedFailuresAreDeferredAndLaterEligible() {
        Long id = newDeletedPost();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(posts.claimPurge(id, "first", now, now.plusMinutes(15))).isEqualTo(1);
            assertThat(posts.releasePurgeForRetry(id, "first", now.plusMinutes(10))).isEqualTo(1);
        });
        assertThat(posts.findDuePurgeIds(now, 1, PageRequest.of(0, 50))).doesNotContain(id);
        assertThat(posts.findDuePurgeIds(now.plusMinutes(11), 1, PageRequest.of(0, 50))).contains(id);
    }

    @Test
    void pendingDeletionOwnerWithOpenModerationIsNotPurged() {
        Long id = newDeletedPost();
        Long authorId = jdbc.queryForObject("SELECT author_id FROM cheer_post WHERE id = ?", Long.class, id);
        jdbc.update("UPDATE users SET pending_deletion = ? WHERE id = ?", true, authorId);
        jdbc.update("INSERT INTO cheer_post_reports (post_id, reporter_id, reason, status, appeal_status, appeal_count, createdat) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, authorId, "OTHER", "PENDING", "NONE", 0, LocalDateTime.now());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        assertThat(posts.findDuePurgeIds(now, 0, PageRequest.of(0, 50))).doesNotContain(id);
        Integer claim = new TransactionTemplate(transactionManager).execute(status ->
                posts.claimPurge(id, "blocked", now, now.plusMinutes(15)));
        assertThat(claim).isZero();
        jdbc.update("UPDATE cheer_post_reports SET status = ? WHERE post_id = ?", "RESOLVED", id);
        Integer afterResolution = new TransactionTemplate(transactionManager).execute(status ->
                posts.claimPurge(id, "allowed", now, now.plusMinutes(15)));
        assertThat(afterResolution).isEqualTo(1);
    }

    @Test
    void newPurgeWaitsUntilPendingAccountDeletionIsFinalized() {
        Long id = newDeletedPost();
        Long authorId = jdbc.queryForObject("SELECT author_id FROM cheer_post WHERE id = ?", Long.class, id);
        jdbc.update("UPDATE users SET pending_deletion = ?, deletion_scheduled_for = ? WHERE id = ?",
                true, LocalDateTime.now().plusDays(1), authorId);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        assertThat(posts.findDuePurgeIds(now, 0, PageRequest.of(0, 50))).doesNotContain(id);
        Integer claim = new TransactionTemplate(transactionManager).execute(status ->
                posts.claimPurge(id, "blocked", now, now.plusMinutes(15)));
        assertThat(claim).isZero();
    }

    @Test
    void reservationConflictRollsBackPostClaim() {
        Long postId = newDeletedPost();
        Long ownerId = jdbc.queryForObject("SELECT author_id FROM cheer_post WHERE id = ?", Long.class, postId);
        String key = "media/cheer/" + ownerId + "/" + UUID.randomUUID() + ".webp";
        Long assetId = new TransactionTemplate(transactionManager).execute(status -> {
            MediaAsset asset = assets.saveAndFlush(MediaAsset.builder().ownerUserId(ownerId)
                    .domain(MediaDomain.CHEER).status(MediaAssetStatus.READY).objectKey(key)
                    .stagingObjectKey("synthetic-stage-" + UUID.randomUUID())
                    .declaredContentType("image/webp").declaredBytes(10L).declaredWidth(1).declaredHeight(1)
                    .uploadExpiresAt(LocalDateTime.now().plusHours(1)).build());
            mediaLinks.syncCheerLinks(postId, ownerId, java.util.List.of(key));
            return asset.getId();
        });
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    users.findByIdForWrite(ownerId).orElseThrow();
                    assertThat(posts.claimPurge(postId, "synthetic-claim", now, now.plusMinutes(15))).isEqualTo(1);
                    reservation.reserve(postId + 1, java.util.List.of(key));
                })).isInstanceOf(com.example.common.exception.ConflictBusinessException.class);
        assertThat(jdbc.queryForObject("SELECT purge_started_at FROM cheer_post WHERE id = ?", LocalDateTime.class, postId))
                .isNull();
        assertThat(assets.findById(assetId).orElseThrow().getStatus()).isEqualTo(MediaAssetStatus.READY);
    }

    @Test
    void sharedLegacyImagePathCannotBeReservedForDeletion() {
        Long first = newDeletedPost();
        Long second = newDeletedPost();
        String path = "posts/shared/synthetic.webp";
        for (Long id : java.util.List.of(first, second)) {
            jdbc.update("INSERT INTO post_images (post_id, storage_path, mime_type, bytes, is_thumbnail, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    id, path, "image/webp", 10, false, java.time.Instant.now());
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> reservation.reserve(first, java.util.List.of(path))))
                .isInstanceOf(com.example.common.exception.ConflictBusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM post_images WHERE storage_path = ?", Long.class, path))
                .isEqualTo(2L);
    }

    @Test
    void pendingAssetOwnerWithOpenReportProtectsAnotherAuthorsPost() {
        Long targetPostId = newDeletedPost();
        Long ownerEvidencePost = newDeletedPost();
        Long ownerId = jdbc.queryForObject("SELECT author_id FROM cheer_post WHERE id = ?", Long.class, ownerEvidencePost);
        String key = "media/cheer/" + ownerId + "/" + UUID.randomUUID() + ".webp";
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assets.saveAndFlush(MediaAsset.builder().ownerUserId(ownerId)
                    .domain(MediaDomain.CHEER).status(MediaAssetStatus.READY).objectKey(key)
                    .stagingObjectKey("synthetic-stage-" + UUID.randomUUID())
                    .declaredContentType("image/webp").declaredBytes(10L).declaredWidth(1).declaredHeight(1)
                    .uploadExpiresAt(LocalDateTime.now().plusHours(1)).build());
            mediaLinks.syncCheerLinks(targetPostId, ownerId, java.util.List.of(key));
        });
        jdbc.update("UPDATE users SET pending_deletion = ?, deletion_scheduled_for = NULL WHERE id = ?", true, ownerId);
        jdbc.update("INSERT INTO cheer_post_reports (post_id, reporter_id, reason, status, appeal_status, appeal_count, createdat) VALUES (?, ?, ?, ?, ?, ?, ?)",
                ownerEvidencePost, ownerId, "OTHER", "PENDING", "NONE", 0, LocalDateTime.now());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        assertThat(posts.findDuePurgeIds(now, 0, PageRequest.of(0, 50))).doesNotContain(targetPostId);
        Integer blocked = new TransactionTemplate(transactionManager).execute(status ->
                posts.claimPurge(targetPostId, "blocked-cross-owner", now, now.plusMinutes(15)));
        assertThat(blocked).isZero();
        jdbc.update("UPDATE cheer_post_reports SET status = ? WHERE post_id = ?", "RESOLVED", ownerEvidencePost);
        Integer allowed = new TransactionTemplate(transactionManager).execute(status ->
                posts.claimPurge(targetPostId, "allowed-cross-owner", now, now.plusMinutes(15)));
        assertThat(allowed).isEqualTo(1);
    }

    @Test
    void unclaimedFailureIsDeferredWithoutDelayingAnotherWorkersClaim() {
        Long failedId = newDeletedPost();
        Long claimedId = newDeletedPost();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(posts.claimPurge(claimedId, "active-worker", now, now.plusMinutes(15))).isEqualTo(1);
            assertThat(posts.deferUnclaimedPurge(failedId, now, now.plusMinutes(10))).isEqualTo(1);
            assertThat(posts.deferUnclaimedPurge(claimedId, now, now.plusMinutes(10))).isZero();
        });
        assertThat(posts.findDuePurgeIds(now, 0, PageRequest.of(0, 100))).doesNotContain(failedId);
        assertThat(posts.findDuePurgeIds(now.plusMinutes(11), 0, PageRequest.of(0, 100))).contains(failedId);
        assertThat(jdbc.queryForObject("SELECT purge_claim_token FROM cheer_post WHERE id = ?", String.class, claimedId))
                .isEqualTo("active-worker");
    }

    @Test
    void deferredFailuresDoNotStarveLaterPostsBeyondTheBatch() {
        Long first = newDeletedPost();
        Long authorId = jdbc.queryForObject("SELECT author_id FROM cheer_post WHERE id = ?", Long.class, first);
        var ids = new java.util.ArrayList<Long>();
        ids.add(first);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UserEntity author = users.findById(authorId).orElseThrow();
            TeamEntity team = teams.findById("ZZ").orElseThrow();
            for (int i = 0; i < 50; i++) {
                CheerPost post = posts.saveAndFlush(CheerPost.builder().author(author).team(team)
                        .postType(PostType.NORMAL).content("synthetic backlog").build());
                ids.add(post.getId());
            }
            for (Long id : ids) {
                jdbc.update("UPDATE cheer_post SET deleted = ?, createdat = ? WHERE id = ?", true,
                        java.sql.Timestamp.from(java.time.Instant.parse("2000-01-01T00:00:00Z")), id);
            }
        });
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<Long> firstBatch = posts.findDuePurgeIds(now, 0, PageRequest.of(0, 50));
        assertThat(firstBatch).containsExactlyElementsOf(ids.subList(0, 50));
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                firstBatch.forEach(id -> assertThat(posts.deferUnclaimedPurge(id, now, now.plusMinutes(10))).isEqualTo(1)));
        assertThat(posts.findDuePurgeIds(now, 0, PageRequest.of(0, 50))).contains(ids.get(50));
    }
}
