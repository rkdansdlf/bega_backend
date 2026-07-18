package com.example.common.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

@DataJpaTest
@Import({RealtimeOutboxStateService.class, RealtimeOutboxProperties.class})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:realtime_outbox;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RealtimeOutboxPersistenceTest {

    @Autowired
    private RealtimeOutboxRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private RealtimeOutboxStateService stateService;

    @Test
    void onlyOneWorkerCanClaimTheSamePendingEvent() {
        Instant now = Instant.now();
        Long id = inTransaction(() -> repository.save(RealtimeOutboxEvent.pending(
                RealtimeMessageEnvelope.broadcast(
                        "event-claim",
                        "/topic/party/5",
                        new ObjectMapper().valueToTree(Map.of("message", "hello"))),
                "{\"message\":\"hello\"}",
                now)).getId());

        int first = inTransaction(() -> repository.claim(
                id, "worker-a", now, now, now.plusSeconds(30)));
        int second = inTransaction(() -> repository.claim(
                id, "worker-b", now, now, now.plusSeconds(30)));

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
    }

    @Test
    void expiredProcessingLeaseCanBeReclaimed() {
        Instant now = Instant.now();
        Long id = inTransaction(() -> repository.save(RealtimeOutboxEvent.pending(
                RealtimeMessageEnvelope.broadcast(
                        "event-expired",
                        "/topic/party/5",
                        new ObjectMapper().createObjectNode()),
                "{}",
                now.minusSeconds(60))).getId());
        assertThat(inTransaction(() -> repository.claim(
                id,
                "worker-a",
                now.minusSeconds(60),
                now.minusSeconds(60),
                now.minusSeconds(30))))
                .isEqualTo(1);

        assertThat(inTransaction(() -> repository.claim(
                id, "worker-b", now, now, now.plusSeconds(30))))
                .isEqualTo(1);
        assertThat(repository.findById(id).orElseThrow().getAttemptCount()).isEqualTo(2);
    }

    @Test
    void concurrentWorkersProduceExactlyOneClaimWinner() throws Exception {
        Instant now = Instant.now();
        Long id = savePending("event-concurrent-claim", now);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = executor.submit(() -> concurrentClaim(
                    id, "worker-a", now, ready, start));
            Future<Integer> second = executor.submit(() -> concurrentClaim(
                    id, "worker-b", now, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS) + second.get(5, TimeUnit.SECONDS))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void domainRollbackAlsoRollsBackOutboxWrite() {
        RealtimeOutboxWriter writer = new RealtimeOutboxWriter(repository, new ObjectMapper());
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        long countBefore = repository.count();

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            writer.broadcast("/topic/party/5", Map.of("message", "hello"));
            throw new IllegalStateException("domain write failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(repository.count()).isEqualTo(countBefore);
    }

    @Test
    void failedClaimCanBeRetriedAndMarkedPublished() {
        Instant now = Instant.now();
        Long id = savePending("event-retry", now);

        RealtimeOutboxClaim firstClaim = stateService.claim(
                id, "worker-a", now, java.time.Duration.ofSeconds(30)).orElseThrow();
        assertThat(firstClaim.attemptCount()).isEqualTo(1);
        assertThat(stateService.markFailure(
                id,
                "worker-a",
                RealtimeOutboxStatus.RETRY,
                now,
                "temporary failure")).isTrue();

        RealtimeOutboxClaim retryClaim = stateService.claim(
                id, "worker-b", now.plusMillis(1), java.time.Duration.ofSeconds(30)).orElseThrow();
        assertThat(retryClaim.eventId()).isEqualTo("event-retry");
        assertThat(retryClaim.attemptCount()).isEqualTo(2);
        assertThat(stateService.markPublished(id, "worker-b", now.plusSeconds(1))).isTrue();

        RealtimeOutboxEvent published = repository.findById(id).orElseThrow();
        assertThat(published.getStatus()).isEqualTo(RealtimeOutboxStatus.PUBLISHED);
        assertThat(published.getLockedBy()).isNull();
        assertThat(published.getPublishedAt()).isEqualTo(now.plusSeconds(1));
    }

    @Test
    void cleanupDeletesOnlyPublishedEventsOlderThanRetentionCutoff() {
        Instant now = Instant.now();
        Long oldId = savePending("event-old", now.minusSeconds(120));
        Long recentId = savePending("event-recent", now.minusSeconds(30));
        stateService.claim(oldId, "worker-a", now.minusSeconds(110), java.time.Duration.ofSeconds(30));
        stateService.markPublished(oldId, "worker-a", now.minusSeconds(100));
        stateService.claim(recentId, "worker-b", now.minusSeconds(20), java.time.Duration.ofSeconds(30));
        stateService.markPublished(recentId, "worker-b", now.minusSeconds(10));

        int deleted = stateService.cleanupPublishedBefore(now.minusSeconds(60), 100);

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findById(oldId)).isEmpty();
        assertThat(repository.findById(recentId)).isPresent();
    }

    private Long savePending(String eventId, Instant now) {
        return inTransaction(() -> repository.save(RealtimeOutboxEvent.pending(
                RealtimeMessageEnvelope.broadcast(
                        eventId,
                        "/topic/party/5",
                        new ObjectMapper().createObjectNode()),
                "{}",
                now)).getId());
    }

    private int concurrentClaim(
            Long id,
            String workerId,
            Instant now,
            CountDownLatch ready,
            CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("claim start timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return inTransaction(() -> repository.claim(
                id,
                workerId,
                now,
                now,
                now.plusSeconds(30)));
    }

    private <T> T inTransaction(java.util.concurrent.Callable<T> callback) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> {
            try {
                return callback.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }
}
