package com.example.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import(PhysicalMediaCreationLock.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PhysicalMediaCreationLockIntegrationTest {
    @Autowired PhysicalMediaCreationLock creationLock;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void sameStripeWaitsUntilFirstTransactionCommits() throws Exception {
        jdbc.update("INSERT INTO physical_media_lock_stripes (stripe_id) VALUES (0)");
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicLong firstSession = new AtomicLong();
        AtomicLong secondSession = new AtomicLong();
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                firstSession.set(sessionId());
                creationLock.lock("00" + "0".repeat(62));
                locked.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                return null;
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var second = workers.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                secondSession.set(sessionId());
                secondStarted.countDown();
                creationLock.lock("40" + "0".repeat(62));
                return null;
            }));
            assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            Long blocker = null;
            while (System.nanoTime() < deadline) {
                blocker = jdbc.query("SELECT BLOCKER_ID FROM INFORMATION_SCHEMA.SESSIONS WHERE SESSION_ID = ?", rs -> {
                    if (!rs.next()) return null;
                    Number value = (Number) rs.getObject(1);
                    return value == null ? null : value.longValue();
                }, secondSession.get());
                if (blocker != null && blocker == firstSession.get()) break;
                Thread.sleep(10);
            }
            assertThat(blocker).isEqualTo(firstSession.get());
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void rejectsMissingStripeAndInvalidDigest() {
        TransactionTemplate tx = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> creationLock.lock("01" + "0".repeat(62))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> creationLock.lock("GG" + "0".repeat(62))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> creationLock.lock("AA" + "0".repeat(62))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> creationLock.lock("0".repeat(63))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private long sessionId() {
        return ((Number) entityManager.createNativeQuery("SELECT SESSION_ID()").getSingleResult()).longValue();
    }
}
