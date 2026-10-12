package com.example.cheerboard.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cheerboard.entity.CheerVoteEntity;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:cheer_vote_count_concurrent_insert;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=30000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.show_sql=false"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CheerVoteCountConcurrentInsertIntegrationTest {

    private static final long TIMEOUT_SECONDS = 10;
    private static final String GAME_ID = "test-game-20261012";
    private static final String TEAM_ID = "TEST";

    @Autowired
    private CheerVoteRepository cheerVoteRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("concurrent first votes preserve both aggregate increments after insert conflict")
    void concurrentFirstVote_recoversAfterUniqueInsertConflict() throws Exception {
        CountDownLatch bothRequestsObservedMissingRow = new CountDownLatch(2);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> first = executor.submit(() -> recordFirstVote(transaction, bothRequestsObservedMissingRow));
            Future<Void> second = executor.submit(() -> recordFirstVote(transaction, bothRequestsObservedMissingRow));

            first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            bothRequestsObservedMissingRow.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(cheerVoteRepository.findVoteCount(GAME_ID, TEAM_ID)).isEqualTo(2);
    }

    private Void recordFirstVote(
            TransactionTemplate transaction,
            CountDownLatch bothRequestsObservedMissingRow) {
        Integer updatedRows = transaction.execute(status -> cheerVoteRepository.incrementVoteCount(GAME_ID, TEAM_ID));
        assertThat(updatedRows).isZero();
        bothRequestsObservedMissingRow.countDown();
        await(bothRequestsObservedMissingRow);

        try {
            cheerVoteRepository.saveAndFlush(newVote());
            Integer insertedRowUpdate = transaction.execute(
                    status -> cheerVoteRepository.incrementVoteCount(GAME_ID, TEAM_ID));
            assertThat(insertedRowUpdate).isEqualTo(1);
        } catch (DataIntegrityViolationException race) {
            Integer retryUpdatedRows = transaction.execute(
                    status -> cheerVoteRepository.incrementVoteCount(GAME_ID, TEAM_ID));
            assertThat(retryUpdatedRows).isEqualTo(1);
        }
        return null;
    }

    private CheerVoteEntity newVote() {
        return CheerVoteEntity.builder().gameId(GAME_ID).teamId(TEAM_ID).voteCount(0).build();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while coordinating concurrent vote inserts");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating concurrent vote inserts", exception);
        }
    }
}
