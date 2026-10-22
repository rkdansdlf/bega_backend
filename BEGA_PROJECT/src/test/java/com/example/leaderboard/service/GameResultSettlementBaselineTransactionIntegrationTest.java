package com.example.leaderboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.kbo.repository.GameRepository;
import com.example.leaderboard.entity.GameResultSettlementBaseline;
import com.example.leaderboard.repository.GameResultSettlementBaselineRepository;
import com.example.leaderboard.repository.LegacyPerfectDayDateRepository;
import com.example.prediction.Prediction;
import com.example.prediction.PredictionRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import({
        GameResultSettlementBaselineTransactionIntegrationTest.RepositoryScanConfig.class,
        GameResultSettlementBaselineService.class
})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:game_result_settlement_tx;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=1000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.show_sql=false"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GameResultSettlementBaselineTransactionIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableJpaRepositories(basePackageClasses = {
            PredictionRepository.class,
            GameResultSettlementBaselineRepository.class,
            LegacyPerfectDayDateRepository.class
    })
    static class RepositoryScanConfig {
    }

    @Autowired
    private GameResultSettlementBaselineRepository baselineRepository;

    @Autowired
    private PredictionRepository predictionRepository;

    @Autowired
    private GameResultSettlementBaselineService baselineService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private GameRepository gameRepository;

    @BeforeEach
    void prepareBaselineRow() {
        predictionRepository.deleteAll();
        baselineRepository.deleteAll();
        baselineRepository.saveAndFlush(new GameResultSettlementBaseline(1L, 0L, 0L, false));
    }

    @Test
    void baselinePreparationReleasesItsRowLockBeforePendingBatchReservation() {
        TransactionTemplate outerTransaction = new TransactionTemplate(transactionManager);

        Boolean completed = outerTransaction.execute(status -> {
            assertThat(baselineService.prepareForScoring()).isTrue();
            assertThat(baselineService.reserveNextPendingGameBatch()).isEmpty();
            return true;
        });

        assertThat(completed).isTrue();
    }

    @Test
    void rolledBackPendingBatchKeepsReceiptPendingAndCanBeRetriedAfterCursorWrap() {
        String gameId = "late-game-rollback";
        Prediction prediction = predictionRepository.saveAndFlush(Prediction.builder()
                .gameId(gameId)
                .userId(905L)
                .votedTeam("home")
                .build());
        baselineRepository.deleteAll();
        baselineRepository.saveAndFlush(new GameResultSettlementBaseline(
                GameResultSettlementBaseline.SINGLETON_ID,
                prediction.getId(),
                0L,
                true));

        TransactionTemplate failedBatch = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> failedBatch.execute(status -> {
            List<String> reservedGameIds = baselineService.reserveNextPendingGameBatch();
            assertThat(reservedGameIds).containsExactly(gameId);
            assertThat(predictionRepository.claimSettlement(
                    prediction.getId(),
                    LocalDateTime.now(),
                    Prediction.ScoreSettlementStatus.PENDING,
                    Prediction.ScoreSettlementStatus.SETTLED)).isEqualTo(1);
            throw new IllegalStateException("simulated scoring transaction failure");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated scoring transaction failure");

        assertThat(predictionRepository.findById(prediction.getId()).orElseThrow()
                .getScoreSettlementStatus()).isEqualTo(Prediction.ScoreSettlementStatus.PENDING);
        assertThat(baselineRepository.findById(GameResultSettlementBaseline.SINGLETON_ID).orElseThrow()
                .getPendingGameCursor()).isEqualTo(gameId);

        assertThat(baselineService.reserveNextPendingGameBatch()).isEmpty();
        assertThat(baselineRepository.findById(GameResultSettlementBaseline.SINGLETON_ID).orElseThrow()
                .getPendingGameCursor()).isNull();
        assertThat(baselineService.reserveNextPendingGameBatch()).containsExactly(gameId);

        TransactionTemplate retryBatch = new TransactionTemplate(transactionManager);
        Integer claimed = retryBatch.execute(status -> predictionRepository.claimSettlement(
                prediction.getId(),
                LocalDateTime.now(),
                Prediction.ScoreSettlementStatus.PENDING,
                Prediction.ScoreSettlementStatus.SETTLED));

        assertThat(claimed).isEqualTo(1);
        assertThat(predictionRepository.findById(prediction.getId()).orElseThrow()
                .getScoreSettlementStatus()).isEqualTo(Prediction.ScoreSettlementStatus.SETTLED);
    }
}
