package com.example.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.ConflictBusinessException;
import com.example.media.entity.MediaAsset;
import com.example.media.entity.MediaAssetStatus;
import com.example.media.entity.MediaDomain;
import com.example.media.repository.MediaAssetRepository;
import com.example.media.repository.MediaAssetLinkRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({CheerMediaPurgeReservation.class, MediaLinkService.class})
class CheerMediaPurgeReservationTest {
    @Autowired MediaAssetRepository assets;
    @Autowired MediaAssetLinkRepository links;
    @Autowired MediaLinkService mediaLinks;
    @Autowired CheerMediaPurgeReservation reservation;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;

    private MediaAsset ready(String key) {
        return assets.saveAndFlush(MediaAsset.builder().ownerUserId(17L).domain(MediaDomain.CHEER)
                .status(MediaAssetStatus.READY).declaredContentType("image/webp").declaredBytes(10L)
                .declaredWidth(1).declaredHeight(1).stagingObjectKey("staging/" + key)
                .objectKey(key).uploadExpiresAt(LocalDateTime.now().plusHours(1)).build());
    }

    @Test
    void reservationPreservesLinkAndRejectsReuseEvenOnRetry() {
        String key = "media/cheer/17/synthetic.webp";
        MediaAsset asset = ready(key);
        mediaLinks.syncCheerLinks(101L, 17L, List.of(key));
        reservation.reserve(101L, List.of(key));
        reservation.reserve(101L, List.of(key));
        assets.flush();
        assertThat(assets.findById(asset.getId()).orElseThrow().getStatus())
                .isEqualTo(MediaAssetStatus.CHEER_PURGE_PENDING);
        assertThat(links.countCheerPostLink(asset.getId(), 101L)).isEqualTo(1);
        assertThatThrownBy(() -> mediaLinks.syncCheerLinks(102L, 17L, List.of(key)))
                .isInstanceOf(BadRequestBusinessException.class)
                .extracting("code").isEqualTo("MEDIA_ASSET_NOT_READY");
    }

    @Test
    void reservationRejectsMissingOrDifferentPostLinkWithoutChangingStatus() {
        String key = "media/cheer/17/synthetic.webp";
        MediaAsset asset = ready(key);
        mediaLinks.syncCheerLinks(101L, 17L, List.of(key));
        assertThatThrownBy(() -> reservation.reserve(102L, List.of(key)))
                .isInstanceOf(ConflictBusinessException.class);
        assertThat(asset.getStatus()).isEqualTo(MediaAssetStatus.READY);
    }

    @Test
    void reservationRejectsMissingLinkAndOtherManagedDomains() {
        String key = "media/cheer/17/unlinked.webp";
        MediaAsset asset = ready(key);
        assertThatThrownBy(() -> reservation.reserve(101L, List.of(key)))
                .isInstanceOf(ConflictBusinessException.class);
        assertThat(asset.getStatus()).isEqualTo(MediaAssetStatus.READY);
        assertThatThrownBy(() -> reservation.reserve(101L, List.of("media/chat/17/foreign.webp")))
                .isInstanceOf(ConflictBusinessException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void reservationRechecksLinkAfterAnotherTransactionRemovesIt() throws Exception {
        String key = "media/cheer/17/stale-link.webp";
        Long id = new TransactionTemplate(transactionManager).execute(status -> {
            MediaAsset asset = ready(key);
            mediaLinks.syncCheerLinks(101L, 17L, List.of(key));
            return asset.getId();
        });
        CountDownLatch readOldLink = new CountDownLatch(1);
        CountDownLatch removed = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var attempt = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                links.findById(id).orElseThrow();
                readOldLink.countDown();
                try {
                    if (!removed.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("unlink did not complete");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                reservation.reserve(101L, List.of(key));
                return null;
            }));
            try {
                assertThat(readOldLink.await(5, TimeUnit.SECONDS)).isTrue();
                mediaLinks.unlinkEntity(MediaDomain.CHEER, 101L);
            } finally {
                removed.countDown();
            }
            assertThatThrownBy(() -> attempt.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(ConflictBusinessException.class);
        }
        assertThat(assets.findById(id).orElseThrow().getStatus()).isEqualTo(MediaAssetStatus.READY);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void oppositeKeyOrderDoesNotReverseAssetLockOrder() throws Exception {
        String firstKey = "media/cheer/17/order-a.webp";
        String secondKey = "media/cheer/17/order-b.webp";
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ready(firstKey);
            ready(secondKey);
            mediaLinks.syncCheerLinks(202L, 17L, List.of(firstKey, secondKey));
        });
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                        reservation.reserve(202L, List.of(firstKey, secondKey)));
                return true;
            });
            var second = executor.submit(() -> {
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                        reservation.reserve(202L, List.of(secondKey, firstKey)));
                return true;
            });
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(assets.findByObjectKeyIn(List.of(firstKey, secondKey)))
                .extracting(MediaAsset::getStatus)
                .containsOnly(MediaAssetStatus.CHEER_PURGE_PENDING);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cheerLinkWriterWaitsForReservationAndRechecksReadyStatus() throws Exception {
        String key = "media/cheer/17/locking.webp";
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ready(key);
            mediaLinks.syncCheerLinks(303L, 17L, List.of(key));
        });
        CountDownLatch reserved = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch writerStarted = new CountDownLatch(1);
        var reservationSession = new java.util.concurrent.atomic.AtomicLong();
        var writerSession = new java.util.concurrent.atomic.AtomicLong();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var claim = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                reservationSession.set(((Number) entityManager.createNativeQuery("SELECT SESSION_ID()").getSingleResult()).longValue());
                reservation.reserve(303L, List.of(key));
                reserved.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("release timeout");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                return null;
            }));
            try {
                assertThat(reserved.await(5, TimeUnit.SECONDS)).isTrue();
                var writer = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                    writerSession.set(((Number) entityManager.createNativeQuery("SELECT SESSION_ID()").getSingleResult()).longValue());
                    writerStarted.countDown();
                    mediaLinks.syncCheerLinks(303L, 17L, List.of(key));
                    return null;
                }));
                assertThat(writerStarted.await(5, TimeUnit.SECONDS)).isTrue();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                Long blocker = null;
                while (System.nanoTime() < deadline) {
                    blocker = jdbc.query("SELECT BLOCKER_ID FROM INFORMATION_SCHEMA.SESSIONS WHERE SESSION_ID = ?",
                            rs -> rs.next() && rs.getObject(1) != null ? ((Number) rs.getObject(1)).longValue() : null,
                            writerSession.get());
                    if (blocker != null && blocker == reservationSession.get()) break;
                    Thread.sleep(10);
                }
                assertThat(blocker).isEqualTo(reservationSession.get());
                release.countDown();
                claim.get(10, TimeUnit.SECONDS);
                assertThatThrownBy(() -> writer.get(10, TimeUnit.SECONDS))
                        .hasCauseInstanceOf(BadRequestBusinessException.class);
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void reservationWaitsForLinkWriterAndRechecksRemovedLink() throws Exception {
        String key = "media/cheer/17/unlink-race.webp";
        Long assetId = new TransactionTemplate(transactionManager).execute(status -> {
            MediaAsset asset = ready(key);
            mediaLinks.syncCheerLinks(404L, 17L, List.of(key));
            return asset.getId();
        });
        CountDownLatch unlinked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch claimStarted = new CountDownLatch(1);
        var writerSession = new java.util.concurrent.atomic.AtomicLong();
        var claimSession = new java.util.concurrent.atomic.AtomicLong();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var writer = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                writerSession.set(((Number) entityManager.createNativeQuery("SELECT SESSION_ID()").getSingleResult()).longValue());
                mediaLinks.syncCheerLinks(404L, 17L, List.of());
                unlinked.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("release timeout");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                return null;
            }));
            try {
                assertThat(unlinked.await(5, TimeUnit.SECONDS)).isTrue();
                var claim = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                    claimSession.set(((Number) entityManager.createNativeQuery("SELECT SESSION_ID()").getSingleResult()).longValue());
                    claimStarted.countDown();
                    reservation.reserve(404L, List.of(key));
                    return null;
                }));
                assertThat(claimStarted.await(5, TimeUnit.SECONDS)).isTrue();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                Long blocker = null;
                while (System.nanoTime() < deadline) {
                    blocker = jdbc.query("SELECT BLOCKER_ID FROM INFORMATION_SCHEMA.SESSIONS WHERE SESSION_ID = ?",
                            rs -> rs.next() && rs.getObject(1) != null ? ((Number) rs.getObject(1)).longValue() : null,
                            claimSession.get());
                    if (blocker != null && blocker == writerSession.get()) break;
                    Thread.sleep(10);
                }
                assertThat(blocker).isEqualTo(writerSession.get());
                release.countDown();
                writer.get(10, TimeUnit.SECONDS);
                assertThatThrownBy(() -> claim.get(10, TimeUnit.SECONDS))
                        .hasCauseInstanceOf(ConflictBusinessException.class);
            } finally {
                release.countDown();
            }
        }
        assertThat(assets.findById(assetId).orElseThrow().getStatus()).isEqualTo(MediaAssetStatus.READY);
    }
}
