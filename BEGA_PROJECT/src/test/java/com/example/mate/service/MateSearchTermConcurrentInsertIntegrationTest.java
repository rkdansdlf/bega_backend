package com.example.mate.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.mate.entity.MateSearchTerm;
import com.example.mate.repository.MateSearchTermRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.dao.DataIntegrityViolationException;
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

@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:mate_search_term_concurrent_insert;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.show_sql=false"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MateSearchTermConcurrentInsertIntegrationTest {

    private static final long TIMEOUT_SECONDS = 10;
    private static final String NORMALIZED_TERM = "kia 응원석";
    private static final String DISPLAY_TERM = "KIA 응원석";

    @Autowired
    private MateSearchTermRepository mateSearchTermRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("a competing daily term insert is recovered and both searches are counted")
    void recordSearchTerm_recoversAfterConcurrentUniqueInsert() throws Exception {
        CountDownLatch bothRequestsObservedMissingTerm = new CountDownLatch(2);

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> first = executor.submit(() -> {
                Instant searchedAt = Instant.now();
                LocalDate searchDate = LocalDate.now();
                transaction.execute(status -> {
                    assertThat(mateSearchTermRepository.incrementDailyTerm(
                            searchDate, NORMALIZED_TERM, DISPLAY_TERM, searchedAt)).isZero();
                    bothRequestsObservedMissingTerm.countDown();
                    await(bothRequestsObservedMissingTerm);
                    return null;
                });
                try {
                    mateSearchTermRepository.saveAndFlush(newTerm(searchDate, searchedAt));
                } catch (DataIntegrityViolationException race) {
                    assertThat(mateSearchTermRepository.incrementDailyTerm(
                            searchDate, NORMALIZED_TERM, DISPLAY_TERM, searchedAt)).isEqualTo(1);
                }
                return null;
            });
            Future<Void> second = executor.submit(() -> {
                Instant searchedAt = Instant.now();
                LocalDate searchDate = LocalDate.now();
                transaction.execute(status -> {
                    assertThat(mateSearchTermRepository.incrementDailyTerm(
                            searchDate, NORMALIZED_TERM, DISPLAY_TERM, searchedAt)).isZero();
                    bothRequestsObservedMissingTerm.countDown();
                    await(bothRequestsObservedMissingTerm);
                    return null;
                });
                try {
                    mateSearchTermRepository.saveAndFlush(newTerm(searchDate, searchedAt));
                } catch (DataIntegrityViolationException race) {
                    assertThat(mateSearchTermRepository.incrementDailyTerm(
                            searchDate, NORMALIZED_TERM, DISPLAY_TERM, searchedAt)).isEqualTo(1);
                }
                return null;
            });
            first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            bothRequestsObservedMissingTerm.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(mateSearchTermRepository.findAll())
                .singleElement()
                .extracting(MateSearchTerm::getSearchCount)
                .isEqualTo(2L);
    }

    private MateSearchTerm newTerm(LocalDate searchDate, Instant searchedAt) {
        return MateSearchTerm.builder()
                .searchDate(searchDate)
                .normalizedTerm(NORMALIZED_TERM)
                .displayTerm(DISPLAY_TERM)
                .searchCount(1L)
                .lastSearchedAt(searchedAt)
                .build();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while coordinating concurrent search inserts");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating concurrent search inserts", exception);
        }
    }
}
