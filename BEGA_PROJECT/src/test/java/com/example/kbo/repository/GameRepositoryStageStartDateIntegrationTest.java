package com.example.kbo.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kbo.entity.GameEntity;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * findFirstStartDateByTypeFromSeasonYear 회귀 테스트.
 *
 * <p>season_id 가 없는 경기, 더미/MOCK 경기, 다른 연도의 경기가 단계 시작일을 앞당기면
 * 포스트시즌 단계가 잘못 분류된다.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:game_repository_stage_start;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
class GameRepositoryStageStartDateIntegrationTest {

    private static final int REGULAR_2025 = 1;
    private static final int KOREAN_SERIES_2025 = 2;
    private static final int KOREAN_SERIES_2024 = 3;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS kbo_seasons");
        jdbcTemplate.execute("""
                CREATE TABLE kbo_seasons (
                    season_id INTEGER PRIMARY KEY,
                    season_year INTEGER NOT NULL,
                    league_type_code INTEGER NOT NULL,
                    start_date DATE
                )
                """);
        jdbcTemplate.update("INSERT INTO kbo_seasons (season_id, season_year, league_type_code) VALUES (?, ?, ?)",
                REGULAR_2025, 2025, 0);
        jdbcTemplate.update("INSERT INTO kbo_seasons (season_id, season_year, league_type_code) VALUES (?, ?, ?)",
                KOREAN_SERIES_2025, 2025, 5);
        jdbcTemplate.update("INSERT INTO kbo_seasons (season_id, season_year, league_type_code) VALUES (?, ?, ?)",
                KOREAN_SERIES_2024, 2024, 5);
    }

    @Test
    void ignoresGamesWithoutSeasonIdSoRegularSeasonGamesNeverBecomeStageStart() {
        // season_id 가 없는 3월 경기가 같은 연도의 한국시리즈 행에 붙어 시작일이 3월이 되면 안 된다.
        persistGame("NO-SEASON-MARCH", LocalDate.of(2025, 3, 25), null, null);
        persistGame("KS-REAL", LocalDate.of(2025, 10, 26), KOREAN_SERIES_2025, false);
        entityManager.flush();
        entityManager.clear();

        assertThat(gameRepository.findFirstStartDateByTypeFromSeasonYear(5, 2025))
                .contains(LocalDate.of(2025, 10, 26));
    }

    @Test
    void skipsDummyAndMockGamesButKeepsUnmarkedRealGames() {
        persistGame("KS-DUMMY", LocalDate.of(2025, 10, 20), KOREAN_SERIES_2025, true);
        persistGame("MOCK-KS", LocalDate.of(2025, 10, 21), KOREAN_SERIES_2025, false);
        persistGame("KS-REAL-UNMARKED", LocalDate.of(2025, 10, 26), KOREAN_SERIES_2025, null);
        persistGame("KS-REAL-LATER", LocalDate.of(2025, 10, 28), KOREAN_SERIES_2025, false);
        entityManager.flush();
        entityManager.clear();

        assertThat(gameRepository.findFirstStartDateByTypeFromSeasonYear(5, 2025))
                .contains(LocalDate.of(2025, 10, 26));
    }

    @Test
    void doesNotMixLeagueTypesOrSeasonYears() {
        persistGame("REGULAR-2025", LocalDate.of(2025, 4, 1), REGULAR_2025, false);
        persistGame("KS-2024", LocalDate.of(2024, 10, 22), KOREAN_SERIES_2024, false);
        entityManager.flush();
        entityManager.clear();

        // 2025 한국시리즈 경기는 아직 없다. 정규시즌/지난해 경기가 대신 잡히면 안 된다.
        assertThat(gameRepository.findFirstStartDateByTypeFromSeasonYear(5, 2025)).isEmpty();
        // 와일드카드 행 자체가 없는 단계도 비어 있어야 한다.
        assertThat(gameRepository.findFirstStartDateByTypeFromSeasonYear(2, 2025)).isEmpty();
        assertThat(gameRepository.findFirstStartDateByTypeFromSeasonYear(0, 2025))
                .contains(LocalDate.of(2025, 4, 1));
    }

    private void persistGame(String gameId, LocalDate gameDate, Integer seasonId, Boolean isDummy) {
        entityManager.persist(GameEntity.builder()
                .gameId(gameId)
                .gameDate(gameDate)
                .seasonId(seasonId)
                .homeTeam("LG")
                .awayTeam("KT")
                .isDummy(isDummy)
                .build());
    }
}
