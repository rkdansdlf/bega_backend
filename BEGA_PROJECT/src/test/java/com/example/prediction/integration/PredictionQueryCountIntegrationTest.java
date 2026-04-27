package com.example.prediction.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.entity.GameInningScoreEntity;
import com.example.kbo.entity.GameMetadataEntity;
import com.example.kbo.entity.GameSummaryEntity;
import com.example.kbo.repository.GameInningScoreRepository;
import com.example.kbo.repository.GameMetadataRepository;
import com.example.kbo.repository.GameRepository;
import com.example.kbo.repository.GameSummaryRepository;
import com.example.kbo.service.LeagueStageResolver;
import com.example.kbo.validation.BaseballDataIntegrityGuard;
import com.example.common.config.CacheConfig;
import com.example.prediction.MatchDto;
import com.example.prediction.MatchRangePageResponseDto;
import com.example.prediction.Prediction;
import com.example.prediction.PredictionController;
import com.example.prediction.PredictionRepository;
import com.example.prediction.PredictionService;
import com.example.prediction.VoteFinalResult;
import com.example.prediction.VoteFinalResultRepository;
import com.example.support.HibernateQueryCountSupport;
import com.example.support.HibernateStatisticsTestConfig;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.example.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DataJpaTest
@Import({
        PredictionService.class,
        LeagueStageResolver.class,
        HibernateStatisticsTestConfig.class,
        PredictionQueryCountIntegrationTest.TransactionAliasConfig.class
})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:prediction_query_count;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
@SuppressWarnings("unchecked")
class PredictionQueryCountIntegrationTest {

    @Autowired
    private PredictionService predictionService;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private LeagueStageResolver leagueStageResolver;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PredictionRepository predictionRepository;

    @Autowired
    private GameMetadataRepository gameMetadataRepository;

    @Autowired
    private GameInningScoreRepository gameInningScoreRepository;

    @Autowired
    private GameSummaryRepository gameSummaryRepository;

    @MockitoBean
    private VoteFinalResultRepository voteFinalResultRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private BaseballDataIntegrityGuard baseballDataIntegrityGuard;

    private JdbcTemplate jdbcTemplate;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        mockMvc = MockMvcBuilders.standaloneSetup(new PredictionController(predictionService, predictionRepository)).build();
        jdbcTemplate.execute("DROP TABLE IF EXISTS kbo_seasons");
        jdbcTemplate.execute("""
                CREATE TABLE kbo_seasons (
                    season_id INTEGER PRIMARY KEY,
                    season_year INTEGER NOT NULL,
                    league_type_code INTEGER NOT NULL,
                    start_date DATE
                )
                """);
        gameSummaryRepository.deleteAll();
        gameInningScoreRepository.deleteAll();
        gameMetadataRepository.deleteAll();
        predictionRepository.deleteAll();
        gameRepository.deleteAll();
        Mockito.reset(
                voteFinalResultRepository,
                userRepository);
    }

    @Test
    @DisplayName("matches range query stays flat for same postseason series")
    void getMatchesByDateRangeWithMetadata_keepsPrepareStatementCountFlatForSameSeries() {
        LocalDate firstGameDate = LocalDate.of(2025, 10, 18);
        seedSeasonRows();

        GameEntity first = saveCompletedSeriesGame("202510180001", firstGameDate, 20254, 4, 2);
        saveCompletedSeriesGame("202510190001", firstGameDate.plusDays(1), 20254, 3, 1);
        saveCompletedSeriesGame("202510200001", firstGameDate.plusDays(2), 20254, 5, 4);
        gameRepository.flush();

        leagueStageResolver.resolveEffectiveLeagueTypeCode(first);

        Statistics statistics = HibernateQueryCountSupport.reset(entityManagerFactory);

        MatchRangePageResponseDto response = predictionService.getMatchesByDateRangeWithMetadata(
                firstGameDate,
                firstGameDate.plusDays(2),
                true,
                0,
                10);

        assertThat(response.getContent()).hasSize(3);
        assertThat(response.getContent())
                .extracting(MatchDto::getSeriesGameNo)
                .containsExactly(1, 2, 3);
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("matches range API keeps query count bounded for same postseason series")
    void getMatchesByRangeApi_keepsPrepareStatementCountFlatForSameSeries() throws Exception {
        LocalDate firstGameDate = LocalDate.of(2025, 10, 18);
        seedSeasonRows();
        GameEntity first = saveCompletedSeriesGame("202510180001", firstGameDate, 20254, 4, 2);
        saveCompletedSeriesGame("202510190001", firstGameDate.plusDays(1), 20254, 3, 1);
        saveCompletedSeriesGame("202510200001", firstGameDate.plusDays(2), 20254, 5, 4);
        gameRepository.flush();

        leagueStageResolver.resolveEffectiveLeagueTypeCode(first);

        Statistics statistics = HibernateQueryCountSupport.reset(entityManagerFactory);

        mockMvc.perform(get("/api/matches/range")
                        .param("startDate", firstGameDate.toString())
                        .param("endDate", firstGameDate.plusDays(2).toString())
                        .param("includePast", "true")
                        .param("withMeta", "true")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].seriesGameNo").value(1))
                .andExpect(jsonPath("$.content[1].seriesGameNo").value(2))
                .andExpect(jsonPath("$.content[2].seriesGameNo").value(3));

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("matches range API without metadata keeps query count bounded for same postseason series")
    void getMatchesByRangeApiWithoutMetadata_keepsPrepareStatementCountFlatForSameSeries() throws Exception {
        LocalDate firstGameDate = LocalDate.of(2025, 10, 18);
        seedSeasonRows();
        GameEntity first = saveCompletedSeriesGame("202510180001", firstGameDate, 20254, 4, 2);
        saveCompletedSeriesGame("202510190001", firstGameDate.plusDays(1), 20254, 3, 1);
        saveCompletedSeriesGame("202510200001", firstGameDate.plusDays(2), 20254, 5, 4);
        gameRepository.flush();

        leagueStageResolver.resolveEffectiveLeagueTypeCode(first);

        Statistics statistics = HibernateQueryCountSupport.reset(entityManagerFactory);

        mockMvc.perform(get("/api/matches/range")
                        .param("startDate", firstGameDate.toString())
                        .param("endDate", firstGameDate.plusDays(2).toString())
                        .param("includePast", "true")
                        .param("withMeta", "false")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].seriesGameNo").value(1))
                .andExpect(jsonPath("$[1].seriesGameNo").value(2))
                .andExpect(jsonPath("$[2].seriesGameNo").value(3));

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("matches range API without metadata stays single-query for regular season window")
    void getMatchesByRangeApiWithoutMetadata_keepsPrepareStatementCountFlatForRegularSeason() throws Exception {
        LocalDate targetDate = LocalDate.of(2026, 4, 10);
        saveSeriesGame("202604100001", targetDate, null, null, null, "SCHEDULED");
        saveSeriesGame("202604100002", targetDate, null, null, null, "SCHEDULED");
        gameRepository.flush();

        Statistics statistics = HibernateQueryCountSupport.reset(entityManagerFactory);

        mockMvc.perform(get("/api/matches/range")
                        .param("startDate", targetDate.toString())
                        .param("endDate", targetDate.toString())
                        .param("includePast", "true")
                        .param("withMeta", "false")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].seriesGameNo").isEmpty())
                .andExpect(jsonPath("$[1].seriesGameNo").isEmpty());

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("single day matches query stays flat for same postseason series")
    void getMatchesByDate_keepsPrepareStatementCountFlatForSameSeries() {
        LocalDate firstGameDate = LocalDate.of(2025, 10, 18);
        seedSeasonRows();

        GameEntity first = saveCompletedSeriesGame("202510180001", firstGameDate, 20254, 4, 2);
        gameRepository.flush();

        leagueStageResolver.resolveEffectiveLeagueTypeCode(first);

        Statistics statistics = HibernateQueryCountSupport.reset(entityManagerFactory);

        List<MatchDto> response = predictionService.getMatchesByDate(firstGameDate);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getSeriesGameNo()).isEqualTo(1);
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("matches API returns stable seriesGameNo for scheduled postseason game")
    void getMatchesApi_returnsStableSeriesGameNoForScheduledPostseasonGame() throws Exception {
        LocalDate firstGameDate = LocalDate.of(2025, 10, 18);
        seedSeasonRows();
        saveCompletedSeriesGame("202510180001", firstGameDate, 20254, 4, 2);
        saveCompletedSeriesGame("202510190001", firstGameDate.plusDays(1), 20254, 3, 1);
        saveSeriesGame("202510210001", firstGameDate.plusDays(3), 20254, null, null, "SCHEDULED");
        gameRepository.flush();

        mockMvc.perform(get("/api/matches")
                        .param("date", firstGameDate.plusDays(3).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].gameId").value("202510210001"))
                .andExpect(jsonPath("$[0].seriesGameNo").value(3))
                .andExpect(jsonPath("$[0].leagueType").value("POST"))
                .andExpect(jsonPath("$[0].postSeasonSeries").value("PO"));
    }

    @Test
    @DisplayName("match day navigation API keeps query count bounded with adjacent date projection")
    void getMatchDayNavigationApi_keepsPrepareStatementCountBounded() throws Exception {
        LocalDate targetDate = LocalDate.of(2025, 10, 19);
        seedSeasonRows();
        saveCompletedSeriesGame("202510180001", targetDate.minusDays(1), 20254, 4, 2);
        saveCompletedSeriesGame("202510190001", targetDate, 20254, 3, 1);
        saveCompletedSeriesGame("202510200001", targetDate.plusDays(1), 20254, 5, 4);
        gameRepository.flush();

        Statistics statistics = HibernateQueryCountSupport.reset(entityManagerFactory);

        mockMvc.perform(get("/api/matches/day")
                        .param("date", targetDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date[0]").value(targetDate.getYear()))
                .andExpect(jsonPath("$.date[1]").value(targetDate.getMonthValue()))
                .andExpect(jsonPath("$.date[2]").value(targetDate.getDayOfMonth()))
                .andExpect(jsonPath("$.games.length()").value(1))
                .andExpect(jsonPath("$.prevDate[0]").value(targetDate.minusDays(1).getYear()))
                .andExpect(jsonPath("$.prevDate[1]").value(targetDate.minusDays(1).getMonthValue()))
                .andExpect(jsonPath("$.prevDate[2]").value(targetDate.minusDays(1).getDayOfMonth()))
                .andExpect(jsonPath("$.nextDate[0]").value(targetDate.plusDays(1).getYear()))
                .andExpect(jsonPath("$.nextDate[1]").value(targetDate.plusDays(1).getMonthValue()))
                .andExpect(jsonPath("$.nextDate[2]").value(targetDate.plusDays(1).getDayOfMonth()))
                .andExpect(jsonPath("$.hasPrev").value(true))
                .andExpect(jsonPath("$.hasNext").value(true));

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("match bounds API keeps query count flat with bounds projection")
    void getMatchBoundsApi_keepsPrepareStatementCountFlat() throws Exception {
        LocalDate earliest = LocalDate.of(2025, 3, 22);
        LocalDate latest = LocalDate.of(2025, 10, 1);
        saveSeriesGame("202503220001", earliest, null, null, null, "SCHEDULED");
        saveSeriesGame("202510010001", latest, null, null, null, "SCHEDULED");
        gameRepository.flush();

        Statistics statistics = HibernateQueryCountSupport.reset(entityManagerFactory);

        mockMvc.perform(get("/api/matches/bounds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasData").value(true))
                .andExpect(jsonPath("$.earliestGameDate[0]").value(earliest.getYear()))
                .andExpect(jsonPath("$.earliestGameDate[1]").value(earliest.getMonthValue()))
                .andExpect(jsonPath("$.earliestGameDate[2]").value(earliest.getDayOfMonth()))
                .andExpect(jsonPath("$.latestGameDate[0]").value(latest.getYear()))
                .andExpect(jsonPath("$.latestGameDate[1]").value(latest.getMonthValue()))
                .andExpect(jsonPath("$.latestGameDate[2]").value(latest.getDayOfMonth()));

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("vote status API uses single aggregated query when final result is missing")
    void getVoteStatusApi_usesSingleAggregatedQueryWhenFinalResultMissing() throws Exception {
        String gameId = "202510180001";
        predictionRepository.saveAll(List.of(
                Prediction.builder().gameId(gameId).userId(1L).votedTeam("home").build(),
                Prediction.builder().gameId(gameId).userId(2L).votedTeam("home").build(),
                Prediction.builder().gameId(gameId).userId(3L).votedTeam("away").build()
        ));
        predictionRepository.flush();
        Mockito.when(voteFinalResultRepository.findById(gameId)).thenReturn(Optional.empty());

        Statistics statistics = HibernateQueryCountSupport.reset(entityManagerFactory);

        mockMvc.perform(get("/api/predictions/status/{gameId}", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(gameId))
                .andExpect(jsonPath("$.homeVotes").value(2))
                .andExpect(jsonPath("$.awayVotes").value(1))
                .andExpect(jsonPath("$.totalVotes").value(3))
                .andExpect(jsonPath("$.homePercentage").value(67))
                .andExpect(jsonPath("$.awayPercentage").value(33));

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("vote status API skips prediction query when final result exists")
    void getVoteStatusApi_skipsPredictionQueryWhenFinalResultExists() throws Exception {
        String gameId = "202510190001";
        Mockito.when(voteFinalResultRepository.findById(gameId)).thenReturn(Optional.of(
                VoteFinalResult.builder()
                        .gameId(gameId)
                        .finalVotesA(11)
                        .finalVotesB(9)
                        .finalWinner("HOME")
                        .build()
        ));

        Statistics statistics = HibernateQueryCountSupport.reset(entityManagerFactory);

        mockMvc.perform(get("/api/predictions/status/{gameId}", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(gameId))
                .andExpect(jsonPath("$.homeVotes").value(11))
                .andExpect(jsonPath("$.awayVotes").value(9))
                .andExpect(jsonPath("$.totalVotes").value(20))
                .andExpect(jsonPath("$.homePercentage").value(55))
                .andExpect(jsonPath("$.awayPercentage").value(45));

        assertThat(statistics.getPrepareStatementCount()).isZero();
    }

    @Test
    @DisplayName("match detail API keeps query count bounded with joined header query")
    void getMatchDetailApi_keepsPrepareStatementCountBounded() throws Exception {
        String gameId = "202510180001";
        LocalDate gameDate = LocalDate.of(2025, 10, 18);
        saveCompletedSeriesGame(gameId, gameDate, 20254, 4, 2);
        gameMetadataRepository.save(GameMetadataEntity.builder()
                .gameId(gameId)
                .stadiumCode("JMS")
                .stadiumName("잠실야구장")
                .attendance(12345)
                .startTime(java.time.LocalTime.of(18, 30))
                .gameTimeMinutes(185)
                .weather("맑음")
                .build());
        gameInningScoreRepository.save(GameInningScoreEntity.builder()
                .gameId(gameId)
                .inning(1)
                .teamSide("home")
                .teamCode("LG")
                .runs(2)
                .isExtra(false)
                .build());
        gameSummaryRepository.save(GameSummaryEntity.builder()
                .gameId(gameId)
                .summaryType("HIGHLIGHT")
                .playerId(7)
                .playerName("홍길동")
                .detailText("결승타")
                .build());

        Statistics statistics = HibernateQueryCountSupport.reset(entityManagerFactory);

        mockMvc.perform(get("/api/matches/{gameId}", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(gameId))
                .andExpect(jsonPath("$.stadiumName").value("잠실야구장"))
                .andExpect(jsonPath("$.attendance").value(12345))
                .andExpect(jsonPath("$.startTime[0]").value(18))
                .andExpect(jsonPath("$.startTime[1]").value(30))
                .andExpect(jsonPath("$.inningScores.length()").value(1))
                .andExpect(jsonPath("$.inningScores[0].teamCode").value("LG"))
                .andExpect(jsonPath("$.summary.length()").value(1))
                .andExpect(jsonPath("$.summary[0].detail").value("결승타"));

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("match detail API keeps metadata fields null when joined header has no metadata row")
    void getMatchDetailApi_keepsNullMetadataWhenMetadataRowMissing() throws Exception {
        String gameId = "202510190001";
        LocalDate gameDate = LocalDate.of(2025, 10, 19);
        saveCompletedSeriesGame(gameId, gameDate, 20254, 3, 1);
        gameInningScoreRepository.save(GameInningScoreEntity.builder()
                .gameId(gameId)
                .inning(1)
                .teamSide("away")
                .teamCode("HH")
                .runs(1)
                .isExtra(false)
                .build());
        gameSummaryRepository.save(GameSummaryEntity.builder()
                .gameId(gameId)
                .summaryType("MVP")
                .playerId(54)
                .playerName("선수")
                .detailText("호투")
                .build());

        Statistics statistics = HibernateQueryCountSupport.reset(entityManagerFactory);

        mockMvc.perform(get("/api/matches/{gameId}", gameId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(gameId))
                .andExpect(jsonPath("$.stadiumName").isEmpty())
                .andExpect(jsonPath("$.startTime").isEmpty())
                .andExpect(jsonPath("$.attendance").isEmpty())
                .andExpect(jsonPath("$.weather").isEmpty())
                .andExpect(jsonPath("$.gameTimeMinutes").isEmpty())
                .andExpect(jsonPath("$.inningScores.length()").value(1))
                .andExpect(jsonPath("$.summary.length()").value(1));

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("matches and range endpoints agree on korean series stage for same date")
    void matchesAndRangeEndpoints_agreeOnKoreanSeriesStageForSameDate() throws Exception {
        LocalDate koreanSeriesGameDate = LocalDate.of(2025, 10, 29);
        seedSeasonRows();
        saveSeriesGame("202510290001", koreanSeriesGameDate, 20254, null, null, "SCHEDULED");
        gameRepository.flush();

        mockMvc.perform(get("/api/matches")
                        .param("date", koreanSeriesGameDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].gameId").value("202510290001"))
                .andExpect(jsonPath("$[0].postSeasonSeries").value("KS"));

        mockMvc.perform(get("/api/matches/range")
                        .param("startDate", koreanSeriesGameDate.toString())
                        .param("endDate", koreanSeriesGameDate.toString())
                        .param("includePast", "true")
                        .param("withMeta", "false")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].gameId").value("202510290001"))
                .andExpect(jsonPath("$[0].postSeasonSeries").value("KS"));
    }

    @Test
    @DisplayName("past games API returns latest seven completed game dates in ascending order")
    void getPastGamesApi_returnsLatestSevenCompletedGameDatesInAscendingOrder() throws Exception {
        LocalDate today = LocalDate.now();

        for (int i = 1; i <= 8; i++) {
            LocalDate gameDate = today.minusDays(i);
            saveSeriesGame(
                    gameDate.format(DateTimeFormatter.BASIC_ISO_DATE) + "0001",
                    gameDate,
                    null,
                    5,
                    3,
                    "COMPLETED");
        }
        gameRepository.flush();

        mockMvc.perform(get("/api/games/past"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].gameId").value(today.minusDays(7).format(DateTimeFormatter.BASIC_ISO_DATE) + "0001"))
                .andExpect(jsonPath("$[6].gameId").value(today.minusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE) + "0001"));
    }

    private void seedSeasonRows() {
        jdbcTemplate.update(
                "INSERT INTO kbo_seasons (season_id, season_year, league_type_code, start_date) VALUES (?, ?, ?, ?)",
                20252,
                2025,
                2,
                java.sql.Date.valueOf(LocalDate.of(2025, 10, 5)));
        jdbcTemplate.update(
                "INSERT INTO kbo_seasons (season_id, season_year, league_type_code, start_date) VALUES (?, ?, ?, ?)",
                20253,
                2025,
                3,
                java.sql.Date.valueOf(LocalDate.of(2025, 10, 9)));
        jdbcTemplate.update(
                "INSERT INTO kbo_seasons (season_id, season_year, league_type_code, start_date) VALUES (?, ?, ?, ?)",
                20254,
                2025,
                4,
                java.sql.Date.valueOf(LocalDate.of(2025, 10, 18)));
        jdbcTemplate.update(
                "INSERT INTO kbo_seasons (season_id, season_year, league_type_code, start_date) VALUES (?, ?, ?, ?)",
                20255,
                2025,
                5,
                java.sql.Date.valueOf(LocalDate.of(2025, 10, 26)));
    }

    private GameEntity saveCompletedSeriesGame(
            String gameId,
            LocalDate gameDate,
            Integer seasonId,
            Integer homeScore,
            Integer awayScore) {
        return saveSeriesGame(gameId, gameDate, seasonId, homeScore, awayScore, "COMPLETED");
    }

    private GameEntity saveSeriesGame(
            String gameId,
            LocalDate gameDate,
            Integer seasonId,
            Integer homeScore,
            Integer awayScore,
            String gameStatus) {
        return gameRepository.save(GameEntity.builder()
                .gameId(gameId)
                .gameDate(gameDate)
                .stadium("잠실")
                .homeTeam("LG")
                .awayTeam("HH")
                .homeScore(homeScore)
                .awayScore(awayScore)
                .seasonId(seasonId)
                .gameStatus(gameStatus)
                .isDummy(false)
                .build());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TransactionAliasConfig {

        @Bean(name = {"transactionManager", "kboGameTransactionManager"})
        PlatformTransactionManager predictionQueryCountTransactionManager(
                EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(CacheConfig.PREDICTION_VOTE_STATUS);
        }
    }
}
