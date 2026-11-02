package com.example.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cheerboard.storage.strategy.StorageObjectIdentity;
import com.example.media.repository.PhysicalMediaObjectLockRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import({PhysicalMediaReferenceService.class, PhysicalMediaRegistry.class, PhysicalMediaCreationLock.class,
        PhysicalMediaObjectLockRepository.class})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:physical_reference_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=30000",
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PhysicalMediaReferenceServiceIntegrationTest {
    @Autowired PhysicalMediaReferenceService references;
    @Autowired PhysicalMediaRegistry registry;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach
    void prepare() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS physical_media_refs (source_kind VARCHAR(32) NOT NULL, source_id VARCHAR(128) NOT NULL, source_generation VARCHAR(36) NOT NULL, object_hash VARCHAR(64) NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, PRIMARY KEY(source_kind, source_id, source_generation))");
        for (int id = 0; id < 64; id++) {
            jdbc.update("MERGE INTO physical_media_lock_stripes (stripe_id) KEY(stripe_id) VALUES (?)", id);
        }
    }

    @Test
    void duplicateReferenceIsIdempotentAndAdvancesRevisionOnlyOnce() {
        var object = activeObject();
        var ref = reference(object);
        transaction().executeWithoutResult(status -> references.addAll(List.of(ref, ref)));
        transaction().executeWithoutResult(status -> references.addAll(List.of(ref)));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM physical_media_refs WHERE source_kind = ? AND source_id = ?",
                Integer.class, ref.sourceKind(), ref.sourceId())).isEqualTo(1);
        assertThat(revision(object)).isEqualTo(1L);
    }

    @Test
    void sourceCannotBeReboundAndFailedBatchRollsBack() {
        var first = activeObject();
        var second = activeObject();
        var original = reference(first);
        transaction().executeWithoutResult(status -> references.addAll(List.of(original)));
        var fresh = reference(second);
        var rebound = new PhysicalMediaReferenceService.Reference(original.sourceKind(), original.sourceId(),
                original.sourceGeneration(), second);
        assertThatThrownBy(() -> transaction().executeWithoutResult(status -> references.addAll(List.of(fresh, rebound))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM physical_media_refs WHERE source_kind = ? AND source_id = ?",
                Integer.class, fresh.sourceKind(), fresh.sourceId())).isZero();
        assertThat(revision(second)).isZero();
    }

    @Test
    void registrationWaitsForDeletionTransitionAndRejectsPendingObject() throws Exception {
        var object = activeObject();
        var ref = reference(object);
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var started = new CountDownLatch(1);
        var firstSession = new AtomicLong();
        var secondSession = new AtomicLong();
        var workers = Executors.newFixedThreadPool(2);
        try {
            var deletion = workers.submit(() -> transaction().execute(status -> {
                firstSession.set(sessionId());
                registry.lockOrRegisterAll(List.of(object));
                jdbc.update("UPDATE physical_media_objects SET state = 'DELETE_PENDING', deletion_requested = ? WHERE object_hash = ?",
                        true, object.digest());
                held.countDown();
                await(release);
                return null;
            }));
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
            var adding = workers.submit(() -> transaction().execute(status -> {
                secondSession.set(sessionId());
                started.countDown();
                references.addAll(List.of(ref));
                return null;
            }));
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
            awaitBlockedBy(secondSession.get(), firstSession.get());
            release.countDown();
            deletion.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> adding.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(IllegalStateException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM physical_media_refs WHERE source_kind = ? AND source_id = ?",
                    Integer.class, ref.sourceKind(), ref.sourceId())).isZero();
            assertThat(revision(object)).isZero();
        } finally {
            release.countDown();
            workers.shutdownNow();
        }
    }

    private StorageObjectIdentity activeObject() {
        var object = new StorageObjectIdentity("synthetic-bucket", "synthetic/" + UUID.randomUUID());
        jdbc.update("""
                INSERT INTO physical_media_objects (object_hash, physical_bucket, object_key, state, audit_generation,
                    reference_revision, claim_generation, deletion_requested, first_absence_generation, attempt_count,
                    created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', 1, 0, 0, ?, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, object.digest(), object.bucket(), object.key(), false);
        return object;
    }

    private PhysicalMediaReferenceService.Reference reference(StorageObjectIdentity object) {
        return new PhysicalMediaReferenceService.Reference("POST_IMAGE", UUID.randomUUID().toString(), UUID.randomUUID(), object);
    }

    private long revision(StorageObjectIdentity object) {
        return jdbc.queryForObject("SELECT reference_revision FROM physical_media_objects WHERE object_hash = ?", Long.class, object.digest());
    }

    private long sessionId() {
        return ((Number) entityManager.createNativeQuery("SELECT SESSION_ID()").getSingleResult()).longValue();
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactions);
    }

    private void awaitBlockedBy(long session, long expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        Long blocker = null;
        while (System.nanoTime() < deadline) {
            blocker = jdbc.query("SELECT BLOCKER_ID FROM INFORMATION_SCHEMA.SESSIONS WHERE SESSION_ID = ?", rs -> {
                if (!rs.next()) return null;
                Number value = (Number) rs.getObject(1);
                return value == null ? null : value.longValue();
            }, session);
            if (blocker != null && blocker == expected) return;
            Thread.sleep(10);
        }
        assertThat(blocker).isEqualTo(expected);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("test lock timeout");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
