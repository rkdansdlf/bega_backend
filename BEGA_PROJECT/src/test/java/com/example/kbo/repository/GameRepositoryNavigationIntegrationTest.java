package com.example.kbo.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kbo.entity.GameEntity;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:game_repository_navigation;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
class GameRepositoryNavigationIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private GameRepository gameRepository;

    @Test
    void defaultNavigationSkipsDummyAndMockGamesButIncludesUnmarkedGames() {
        LocalDate selectedDate = LocalDate.of(2026, 4, 10);
        persistGame("REAL-PREV-NULL", selectedDate.minusDays(3), null);
        persistGame("REAL-PREV", selectedDate.minusDays(2), false);
        persistGame("DUMMY-PREV", selectedDate.minusDays(1), true);
        persistGame("MOCK-PREV", selectedDate.minusDays(1), false);
        persistGame("DUMMY-NEXT", selectedDate.plusDays(1), true);
        persistGame("MOCK-NEXT", selectedDate.plusDays(2), false);
        persistGame("REAL-NEXT-NULL", selectedDate.plusDays(3), null);
        persistGame("REAL-NEXT", selectedDate.plusDays(4), false);
        entityManager.flush();
        entityManager.clear();

        assertThat(gameRepository.findPrevGameDate(selectedDate))
                .contains(selectedDate.minusDays(2));
        assertThat(gameRepository.findNextGameDate(selectedDate))
                .contains(selectedDate.plusDays(3));
    }

    private void persistGame(String gameId, LocalDate gameDate, Boolean isDummy) {
        entityManager.persist(GameEntity.builder()
                .gameId(gameId)
                .gameDate(gameDate)
                .homeTeam("LG")
                .awayTeam("KT")
                .isDummy(isDummy)
                .build());
    }
}
