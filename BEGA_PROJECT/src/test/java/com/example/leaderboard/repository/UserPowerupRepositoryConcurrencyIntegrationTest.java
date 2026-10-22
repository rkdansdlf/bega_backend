package com.example.leaderboard.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.repository.GameRepository;
import com.example.leaderboard.entity.ActivePowerup;
import com.example.leaderboard.entity.UserPowerup;
import com.example.leaderboard.entity.UserPowerup.PowerupType;
import com.example.leaderboard.service.PowerupService;
import java.util.Arrays;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import(PowerupService.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:user_powerup_concurrency;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=5000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.show_sql=false"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserPowerupRepositoryConcurrencyIntegrationTest {

    @Autowired
    private UserPowerupRepository userPowerupRepository;

    @Autowired
    private PowerupService powerupService;

    @Autowired
    private GameRepository gameRepository;

    @MockitoBean
    private ActivePowerupRepository activePowerupRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void prepareOneInventoryItem() {
        userPowerupRepository.deleteAllInBatch();
        userPowerupRepository.saveAndFlush(UserPowerup.create(9001L, PowerupType.MAGIC_BAT, 1));
    }

    @Test
    void concurrentConditionalDecrementsConsumeOnlyOneInventoryItem() throws Exception {
        Long userId = 9001L;
        CyclicBarrier bothTransactionsReady = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Integer> first = executor.submit(() -> decrementAfterBarrier(userId, bothTransactionsReady));
            Future<Integer> second = executor.submit(() -> decrementAfterBarrier(userId, bothTransactionsReady));

            assertThat(Arrays.asList(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(1, 0);
        } finally {
            executor.shutdownNow();
        }

        assertThat(userPowerupRepository.findByUserIdAndPowerupType(userId, PowerupType.MAGIC_BAT))
                .get()
                .extracting(UserPowerup::getQuantity)
                .isEqualTo(0);
    }

    @Test
    void activationPersistenceFailureRollsBackInventoryDecrement() {
        gameRepository.saveAndFlush(GameEntity.builder()
                .gameId("game-rollback")
                .gameStatus("SCHEDULED")
                .build());
        when(activePowerupRepository.save(any(ActivePowerup.class)))
                .thenThrow(new IllegalStateException("synthetic activation persistence failure"));

        assertThatThrownBy(() -> powerupService.usePowerup(9001L, "MAGIC_BAT", "game-rollback"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("synthetic activation persistence failure");

        assertThat(userPowerupRepository.findByUserIdAndPowerupType(9001L, PowerupType.MAGIC_BAT))
                .get()
                .extracting(UserPowerup::getQuantity)
                .isEqualTo(1);
    }

    private Integer decrementAfterBarrier(Long userId, CyclicBarrier barrier) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
            } catch (Exception exception) {
                throw new IllegalStateException("Concurrent decrementers did not rendezvous", exception);
            }
            return userPowerupRepository.usePowerup(userId, PowerupType.MAGIC_BAT);
        });
    }
}
