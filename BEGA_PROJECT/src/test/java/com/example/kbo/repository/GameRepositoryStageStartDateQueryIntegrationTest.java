package com.example.kbo.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kbo.entity.GameEntity;
import java.time.LocalDate;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:game_stage_start_date;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=ERROR",
        "logging.level.org.hibernate.orm.jdbc.bind=ERROR"
})
class GameRepositoryStageStartDateQueryIntegrationTest {

    private static final int PLAYOFF = 4;
    private static final int KOREAN_SERIES = 5;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS kbo_seasons");
        jdbcTemplate.execute("""
                CREATE TABLE kbo_seasons (
                    season_id INTEGER PRIMARY KEY,
                    season_year INTEGER NOT NULL,
                    league_type_code INTEGER NOT NULL,
                    start_date DATE
                )
                """);
        gameRepository.deleteAll();
    }

    @Test
    @DisplayName("단계 첫 경기일 집계는 season_id가 NULL인 경기를 어느 단계에도 조인하지 않는다")
    void findFirstStartDateByTypeFromSeasonYear_ignoresNullSeasonGames() {
        insertSeason(20264, 2026, PLAYOFF, null);
        insertSeason(20265, 2026, KOREAN_SERIES, null);
        saveGame("20261013NULL0", LocalDate.of(2026, 10, 13), null, "COMPLETED");
        saveGame("20261018POKT0", LocalDate.of(2026, 10, 18), 20264, "COMPLETED");
        saveGame("20261026KSLG0", LocalDate.of(2026, 10, 26), 20265, "COMPLETED");

        assertThat(gameRepository.findFirstStartDateByTypeFromSeasonYear(PLAYOFF, 2026))
                .contains(LocalDate.of(2026, 10, 18));
        assertThat(gameRepository.findFirstStartDateByTypeFromSeasonYear(KOREAN_SERIES, 2026))
                .contains(LocalDate.of(2026, 10, 26));
    }

    @Test
    @DisplayName("단계 첫 경기일 집계는 실제 진행된 경기만 사용하고 다른 시즌 연도는 섞지 않는다")
    void findFirstStartDateByTypeFromSeasonYear_usesOnlyPlayedGamesOfRequestedSeason() {
        insertSeason(20255, 2025, KOREAN_SERIES, null);
        insertSeason(20265, 2026, KOREAN_SERIES, null);
        saveGame("20251026KSLG0", LocalDate.of(2025, 10, 26), 20255, "COMPLETED");
        saveGame("20261024KSXX0", LocalDate.of(2026, 10, 24), 20265, "CANCELLED");
        saveGame("20261025KSXX0", LocalDate.of(2026, 10, 25), 20265, "SCHEDULED", false, null, null);

        assertThat(gameRepository.findFirstStartDateByTypeFromSeasonYear(KOREAN_SERIES, 2026)).isEmpty();

        saveGame("20261027KSLG0", LocalDate.of(2026, 10, 27), 20265, "live");

        assertThat(gameRepository.findFirstStartDateByTypeFromSeasonYear(KOREAN_SERIES, 2026))
                .contains(LocalDate.of(2026, 10, 27));
    }

    @Test
    @DisplayName("단계 첫 경기일 집계는 더미/MOCK 경기를 제외한다")
    void findFirstStartDateByTypeFromSeasonYear_ignoresDummyAndMockGames() {
        insertSeason(20255, 2025, KOREAN_SERIES, null);
        saveGame("20251020KSDM0", LocalDate.of(2025, 10, 20), 20255, "COMPLETED", true, 3, 2);
        saveGame("MOCK-KS-20251021", LocalDate.of(2025, 10, 21), 20255, "COMPLETED", false, 3, 2);
        saveGame("20251026KSLG0", LocalDate.of(2025, 10, 26), 20255, "COMPLETED");

        assertThat(gameRepository.findFirstStartDateByTypeFromSeasonYear(KOREAN_SERIES, 2025))
                .contains(LocalDate.of(2025, 10, 26));
    }

    @Test
    @DisplayName("원본 상태가 SCHEDULED여도 양 팀 점수가 있는 지난 경기는 진행된 경기로 본다")
    void findFirstStartDateByTypeFromSeasonYear_countsPastScheduledGameWithScores() {
        insertSeason(20255, 2025, KOREAN_SERIES, null);
        saveGame("20251024KSNS0", LocalDate.of(2025, 10, 24), 20255, "SCHEDULED", false, null, null);
        saveGame("20251025KSHS0", LocalDate.of(2025, 10, 25), 20255, "SCHEDULED", false, 4, null);
        saveGame("20251026KSSC0", LocalDate.of(2025, 10, 26), 20255, " scheduled ", false, 5, 1);
        saveGame("20251027KSLG0", LocalDate.of(2025, 10, 27), 20255, "COMPLETED");

        assertThat(gameRepository.findFirstStartDateByTypeFromSeasonYear(KOREAN_SERIES, 2025))
                .contains(LocalDate.of(2025, 10, 26));
    }

    @Test
    @DisplayName("점수가 있어도 아직 오지 않은 SCHEDULED 경기는 진행된 경기로 보지 않는다")
    void findFirstStartDateByTypeFromSeasonYear_ignoresFutureScheduledGameWithScores() {
        int futureYear = LocalDate.now().getYear() + 1;
        int seasonId = futureYear * 10 + KOREAN_SERIES;
        insertSeason(seasonId, futureYear, KOREAN_SERIES, null);
        saveGame(futureYear + "1026KSSC0", LocalDate.of(futureYear, 10, 26), seasonId, "SCHEDULED", false, 0, 0);

        assertThat(gameRepository.findFirstStartDateByTypeFromSeasonYear(KOREAN_SERIES, futureYear)).isEmpty();
    }

    @Test
    @DisplayName("기준일 이전 최근 시작일 조회는 요청 시즌 연도로 제한되어 이전 연도 KS 시작일을 반환하지 않는다")
    void findLatestStartDateByTypeAsOf_doesNotReturnPreviousSeasonStartDate() {
        insertSeason(20255, 2025, KOREAN_SERIES, LocalDate.of(2025, 10, 26));
        insertSeason(20265, 2026, KOREAN_SERIES, null);

        LocalDate asOf = LocalDate.of(2026, 10, 19);
        assertThat(gameRepository.findLatestStartDateByTypeAsOf(KOREAN_SERIES, 2026, asOf)).isEmpty();
        assertThat(gameRepository.findLatestStartDateByTypeAsOf(KOREAN_SERIES, 2025, asOf))
                .contains(LocalDate.of(2025, 10, 26));

        jdbcTemplate.update(
                "UPDATE kbo_seasons SET start_date = ? WHERE season_id = ?",
                java.sql.Date.valueOf(LocalDate.of(2026, 10, 17)),
                20265);

        assertThat(gameRepository.findLatestStartDateByTypeAsOf(KOREAN_SERIES, 2026, asOf))
                .contains(LocalDate.of(2026, 10, 17));
    }

    private void insertSeason(int seasonId, int seasonYear, int leagueTypeCode, LocalDate startDate) {
        jdbcTemplate.update(
                "INSERT INTO kbo_seasons (season_id, season_year, league_type_code, start_date) VALUES (?, ?, ?, ?)",
                seasonId,
                seasonYear,
                leagueTypeCode,
                startDate == null ? null : java.sql.Date.valueOf(startDate));
    }

    private void saveGame(String gameId, LocalDate gameDate, Integer seasonId, String gameStatus) {
        saveGame(gameId, gameDate, seasonId, gameStatus, false, 3, 2);
    }

    private void saveGame(
            String gameId,
            LocalDate gameDate,
            Integer seasonId,
            String gameStatus,
            boolean dummy,
            Integer homeScore,
            Integer awayScore) {
        gameRepository.saveAndFlush(GameEntity.builder()
                .gameId(gameId)
                .gameDate(gameDate)
                .stadium("잠실")
                .homeTeam("LG")
                .awayTeam("HH")
                .homeScore(homeScore)
                .awayScore(awayScore)
                .seasonId(seasonId)
                .gameStatus(gameStatus)
                .isDummy(dummy)
                .build());
    }
}
