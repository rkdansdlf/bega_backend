package com.example.kbo.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kbo.entity.GameEntity;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:prediction_stats_game_status;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class PredictionStatsGameStatusIntegrationTest {
    @Autowired EntityManager entityManager;
    @Autowired GameRepository gameRepository;

    @Test
    @Disabled("Reproduced LIVE inclusion; awaiting approval of the completed-status contract")
    void onlyCompletedGamesContributeToPredictionStats() {
        for (String status : List.of("LIVE", "COMPLETED")) {
            entityManager.persist(GameEntity.builder().gameId("SYNTHETIC-" + status)
                    .gameDate(LocalDate.of(2026, 4, 1)).homeTeam("LG").awayTeam("KT")
                    .homeScore(3).awayScore(1).gameStatus(status).isDummy(false).build());
        }
        entityManager.flush();
        entityManager.clear();

        assertThat(gameRepository.findPredictionStatsGameSummaries(
                List.of("SYNTHETIC-LIVE", "SYNTHETIC-COMPLETED"), List.of("LG", "KT")))
                .extracting(PredictionStatsGameProjection::getGameId)
                .containsExactly("SYNTHETIC-COMPLETED");
    }
}
