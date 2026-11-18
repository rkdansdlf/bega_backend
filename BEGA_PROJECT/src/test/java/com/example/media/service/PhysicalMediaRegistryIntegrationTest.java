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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
@Import({PhysicalMediaRegistry.class, PhysicalMediaCreationLock.class, PhysicalMediaObjectLockRepository.class})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:physical_registry_creation;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=30000",
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PhysicalMediaRegistryIntegrationTest {
    @Autowired PhysicalMediaRegistry registry;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @BeforeEach
    void seedStripes() {
        for (int id = 0; id < 64; id++) {
            jdbc.update("MERGE INTO physical_media_lock_stripes (stripe_id) KEY(stripe_id) VALUES (?)", id);
        }
    }

    @Test
    void duplicateAliasesWithinOneRequestRegisterOnlyOneObject() {
        String path = "synthetic/" + UUID.randomUUID();
        var first = StorageObjectIdentity.of("synthetic-bucket", "synthetic-bucket", path);
        var alias = StorageObjectIdentity.of("synthetic-bucket", "synthetic-bucket", "/synthetic-bucket/" + path);
        var registered = new TransactionTemplate(transactions).execute(status -> registry.lockOrRegisterAll(List.of(first, alias)));
        assertThat(registered).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM physical_media_objects WHERE object_hash = ?",
                Integer.class, first.digest())).isEqualTo(1);
    }

    @Test
    void rejectsSecondRegistryCallInSameTransaction() {
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            registry.lockOrRegisterAll(List.of());
            registry.lockOrRegisterAll(List.of());
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("once per transaction");
    }

    @Test
    void allowsIndependentRequiresNewTransactionAndRestoresOuterGuard() {
        var outer = new TransactionTemplate(transactions);
        var inner = new TransactionTemplate(transactions);
        inner.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        outer.executeWithoutResult(status -> {
            registry.lockOrRegisterAll(List.of());
            inner.executeWithoutResult(innerStatus -> registry.lockOrRegisterAll(List.of()));
            assertThatThrownBy(() -> registry.lockOrRegisterAll(List.of()))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("once per transaction");
            status.setRollbackOnly();
        });
    }

    @Test
    void completionAndRollbackReleaseTheTransactionGuard() {
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> registry.lockOrRegisterAll(List.of()));
        tx.executeWithoutResult(status -> {
            registry.lockOrRegisterAll(List.of());
            status.setRollbackOnly();
        });
        tx.executeWithoutResult(status -> registry.lockOrRegisterAll(List.of()));
    }

    @Test
    void requiresNewRollbackRestoresOuterGuardAndReleasesItAfterCompletion() {
        var outer = new TransactionTemplate(transactions);
        var inner = new TransactionTemplate(transactions);
        inner.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        outer.executeWithoutResult(status -> {
            registry.lockOrRegisterAll(List.of());
            assertThatThrownBy(() -> inner.executeWithoutResult(innerStatus -> {
                registry.lockOrRegisterAll(List.of());
                throw new IllegalArgumentException("synthetic rollback");
            })).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> registry.lockOrRegisterAll(List.of()))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("once per transaction");
            status.setRollbackOnly();
        });
        outer.executeWithoutResult(status -> registry.lockOrRegisterAll(List.of()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentFirstRegistrationHandlesCommitAndRollback(boolean rollBackFirst) throws Exception {
        String path = "synthetic/" + UUID.randomUUID();
        var identity = StorageObjectIdentity.of("synthetic-bucket", "synthetic-bucket", path);
        var alias = StorageObjectIdentity.of("synthetic-bucket", "synthetic-bucket", "/synthetic-bucket/" + path);
        assertThat(alias).isEqualTo(identity);
        CountDownLatch firstRegistered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicLong firstSession = new AtomicLong();
        AtomicLong secondSession = new AtomicLong();
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                firstSession.set(sessionId());
                var objects = registry.lockOrRegisterAll(List.of(identity));
                assertThat(objects).hasSize(1);
                assertThat(objects.getFirst().getState()).isEqualTo(PhysicalObjectLifecycle.State.HOLD_UNVERIFIED);
                firstRegistered.countDown();
                await(release);
                if (rollBackFirst) status.setRollbackOnly();
                return objects.getFirst().getObjectHash();
            }));
            assertThat(firstRegistered.await(10, TimeUnit.SECONDS)).isTrue();
            var second = workers.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                secondSession.set(sessionId());
                secondStarted.countDown();
                return registry.lockOrRegisterAll(List.of(alias)).getFirst().getObjectHash();
            }));
            assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
            awaitBlockedBy(secondSession.get(), firstSession.get());
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(identity.digest());
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(identity.digest());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM physical_media_objects WHERE object_hash = ?",
                    Integer.class, identity.digest())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT state FROM physical_media_objects WHERE object_hash = ?",
                    String.class, identity.digest())).isEqualTo("HOLD_UNVERIFIED");
        } finally {
            release.countDown();
            workers.shutdownNow();
        }
    }

    private long sessionId() {
        return ((Number) entityManager.createNativeQuery("SELECT SESSION_ID()").getSingleResult()).longValue();
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
