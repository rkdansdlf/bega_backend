package com.example.media.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cheerboard.storage.strategy.StorageObjectIdentity;
import com.example.media.entity.PhysicalMediaObject;
import com.example.media.service.PhysicalObjectLifecycle;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import(PhysicalMediaObjectLockRepository.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:physical_object_lock;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=30000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class PhysicalMediaObjectLockRepositoryIntegrationTest {
    @Autowired private PhysicalMediaObjectLockRepository locks;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    @Test
    void missingAndMismatchedIdentitiesFailClosed() {
        var missing = identity();
        assertThatThrownBy(() -> transaction().execute(status -> locks.lockExisting(missing)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not registered");
        assertThatThrownBy(() -> locks.lockExisting(missing)).isInstanceOf(IllegalTransactionStateException.class);

        var wrongBucket = identity();
        insert(wrongBucket, "other-bucket", wrongBucket.key());
        assertThatThrownBy(() -> transaction().execute(status -> locks.lockExisting(wrongBucket)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("identity mismatch");

        var wrongKey = identity();
        insert(wrongKey, wrongKey.bucket(), wrongKey.key() + "-other");
        assertThatThrownBy(() -> transaction().execute(status -> locks.lockExisting(wrongKey)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("identity mismatch");
    }

    @Test
    void refreshChecksTheDatabaseIdentityEvenIfTheEntityWasAlreadyLoaded() {
        var identity = identity();
        insert(identity, identity.bucket(), identity.key());
        assertThatThrownBy(() -> transaction().execute(status -> {
            assertThat(entityManager.find(PhysicalMediaObject.class, identity.digest()).getObjectKey())
                    .isEqualTo(identity.key());
            jdbc.update("UPDATE physical_media_objects SET object_key = ? WHERE object_hash = ?",
                    identity.key() + "-changed", identity.digest());
            return locks.lockExisting(identity);
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("identity mismatch");
    }

    @Test
    void existingRowIsLockedUntilTheCallerTransactionFinishes() throws Exception {
        var identity = identity();
        insert(identity, identity.bucket(), identity.key());
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch secondLocked = new CountDownLatch(1);
        AtomicLong firstSession = new AtomicLong();
        AtomicLong secondSession = new AtomicLong();
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> transaction().execute(status -> {
                firstSession.set(sessionId());
                var object = locks.lockExisting(identity);
                assertThat(object.getState()).isEqualTo(PhysicalObjectLifecycle.State.HOLD_UNVERIFIED);
                firstLocked.countDown();
                await(releaseFirst);
                return object.getObjectHash();
            }));
            assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
            var second = workers.submit(() -> transaction().execute(status -> {
                secondSession.set(sessionId());
                secondStarted.countDown();
                String hash = locks.lockExisting(identity).getObjectHash();
                secondLocked.countDown();
                return hash;
            }));
            assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
            awaitBlockedBy(secondSession.get(), firstSession.get());
            assertThat(secondLocked.getCount()).isEqualTo(1);
            releaseFirst.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(identity.digest());
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(identity.digest());
            assertThat(secondLocked.getCount()).isZero();
        } finally {
            releaseFirst.countDown();
            workers.shutdownNow();
        }
    }

    private void insert(StorageObjectIdentity identity, String bucket, String key) {
        transaction().executeWithoutResult(status -> jdbc.update("""
                INSERT INTO physical_media_objects
                    (object_hash, physical_bucket, object_key, state, audit_generation, reference_revision,
                     claim_generation, deletion_requested, first_absence_generation, attempt_count, created_at, updated_at)
                VALUES (?, ?, ?, 'HOLD_UNVERIFIED', 0, 0, 0, false, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, identity.digest(), bucket, key));
    }

    private StorageObjectIdentity identity() {
        return new StorageObjectIdentity("synthetic-bucket", "synthetic/" + UUID.randomUUID());
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private long sessionId() {
        return ((Number) entityManager.createNativeQuery("SELECT SESSION_ID()").getSingleResult()).longValue();
    }

    private void awaitBlockedBy(long sessionId, long expectedBlocker) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        Long blocker = null;
        while (System.nanoTime() < deadline) {
            blocker = jdbc.query("SELECT BLOCKER_ID FROM INFORMATION_SCHEMA.SESSIONS WHERE SESSION_ID = ?", rs -> {
                if (!rs.next()) return null;
                Number value = (Number) rs.getObject(1);
                return value == null ? null : value.longValue();
            }, sessionId);
            if (blocker != null && blocker == expectedBlocker) return;
            Thread.sleep(10);
        }
        assertThat(blocker).isEqualTo(expectedBlocker);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for test lock");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
