package com.example.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.leaderboard.entity.PerfectDaySettlementReceipt;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@Import(PredictionSettlementRepositoryIntegrationTest.RepositoryScanConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:prediction_settlement_repo;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.show_sql=false",
        "logging.level.org.hibernate.SQL=ERROR",
        "logging.level.org.hibernate.orm.jdbc.bind=ERROR"
})
class PredictionSettlementRepositoryIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableJpaRepositories(basePackageClasses = PredictionRepository.class)
    static class RepositoryScanConfig {
    }

    @Autowired
    private PredictionRepository predictionRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void cleanPredictions() {
        predictionRepository.deleteAll();
    }

    @Test
    @DisplayName("미정산 게임 keyset 조회와 prediction receipt claim은 원자적이고 반복 불가")
    void pendingGameScanAndSettlementClaimAreIdempotent() {
        Prediction late = prediction("late-game", 1L, "home");
        Prediction future = prediction("future-game", 2L, "away");
        Prediction settled = prediction("settled-game", 3L, "home");
        predictionRepository.saveAllAndFlush(List.of(late, future, settled));
        predictionRepository.claimSettlement(
                settled.getId(),
                LocalDateTime.now(),
                Prediction.ScoreSettlementStatus.PENDING,
                Prediction.ScoreSettlementStatus.SETTLED);
        predictionRepository.flush();

        assertThat(predictionRepository.findDistinctPendingGameIdsAfter(
                null,
                Prediction.ScoreSettlementStatus.PENDING,
                PageRequest.of(0, 10)))
                .containsExactly("future-game", "late-game");
        assertThat(predictionRepository.findDistinctPendingGameIdsAfter(
                "future-game",
                Prediction.ScoreSettlementStatus.PENDING,
                PageRequest.of(0, 10)))
                .containsExactly("late-game");

        assertThat(predictionRepository.claimSettlement(
                late.getId(),
                LocalDateTime.now(),
                Prediction.ScoreSettlementStatus.PENDING,
                Prediction.ScoreSettlementStatus.SETTLED)).isEqualTo(1);
        assertThat(predictionRepository.claimSettlement(
                late.getId(),
                LocalDateTime.now(),
                Prediction.ScoreSettlementStatus.PENDING,
                Prediction.ScoreSettlementStatus.SETTLED)).isZero();
    }

    @Test
    @DisplayName("퍼펙트데이 영구 receipt는 사용자·경기일 조합당 하나만 저장")
    void perfectDayReceiptUniqueConstraintIsEnforced() {
        LocalDate gameDate = LocalDate.of(2026, 10, 12);
        entityManager.persist(new PerfectDaySettlementReceipt(50L, gameDate, 4));
        entityManager.flush();

        assertThatThrownBy(() -> {
            entityManager.persist(new PerfectDaySettlementReceipt(50L, gameDate, 4));
            entityManager.flush();
        })
                .isInstanceOf(org.hibernate.exception.ConstraintViolationException.class);
    }

    private static Prediction prediction(String gameId, Long userId, String votedTeam) {
        return Prediction.builder().gameId(gameId).userId(userId).votedTeam(votedTeam).build();
    }
}
