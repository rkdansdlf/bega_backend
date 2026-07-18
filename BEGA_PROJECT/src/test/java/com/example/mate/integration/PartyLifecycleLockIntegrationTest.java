package com.example.mate.integration;

import com.example.mate.entity.Party;
import com.example.mate.repository.PartyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:party_lifecycle_lock;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PartyLifecycleLockIntegrationTest {

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void hostScopedLifecycleMutation_waitsForExistingPartyWriteLock() throws Exception {
        Long partyId = inTransaction(() -> partyRepository.save(newParty()).getId());
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch secondLocked = new CountDownLatch(1);

        Future<?> first = executor.submit(() -> inTransaction(() -> {
            Party party = partyRepository.findByIdForUpdate(partyId).orElseThrow();
            firstLocked.countDown();
            await(releaseFirst);
            party.setDescription("first mutation");
            return null;
        }));

        assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();

        Future<?> second = executor.submit(() -> inTransaction(() -> {
            secondStarted.countDown();
            Party party = partyRepository.findByIdAndHostIdForUpdate(partyId, 10L).orElseThrow();
            secondLocked.countDown();
            party.setSection("second mutation");
            return null;
        }));

        assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(secondLocked.await(250, TimeUnit.MILLISECONDS)).isFalse();

        releaseFirst.countDown();
        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);

        Party saved = partyRepository.findById(partyId).orElseThrow();
        assertThat(saved.getDescription()).isEqualTo("first mutation");
        assertThat(saved.getSection()).isEqualTo("second mutation");
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

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out while waiting for lock test release");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private Party newParty() {
        return Party.builder()
                .hostId(10L)
                .hostName("host")
                .hostBadge(Party.BadgeType.NEW)
                .teamId("LG")
                .gameDate(LocalDate.now().plusDays(1))
                .gameTime(LocalTime.of(18, 30))
                .stadium("잠실")
                .homeTeam("LG")
                .awayTeam("KT")
                .section("original")
                .maxParticipants(4)
                .currentParticipants(1)
                .description("original")
                .ticketVerified(true)
                .status(Party.PartyStatus.PENDING)
                .build();
    }
}
