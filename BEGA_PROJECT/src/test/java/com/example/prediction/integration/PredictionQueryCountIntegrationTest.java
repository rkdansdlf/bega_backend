package com.example.prediction.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.repository.GameInningScoreRepository;
import com.example.kbo.repository.GameMetadataRepository;
import com.example.kbo.repository.GameRepository;
import com.example.kbo.repository.GameSummaryRepository;
import com.example.kbo.service.LeagueStageResolver;
import com.example.prediction.MatchDto;
import com.example.prediction.MatchRangePageResponseDto;
import com.example.prediction.PredictionController;
import com.example.prediction.PredictionRepository;
import com.example.prediction.PredictionService;
import com.example.prediction.VoteFinalResultRepository;
import com.example.support.HibernateQueryCountSupport;
import com.example.support.HibernateStatisticsTestConfig;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import javax.sql.DataSource;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.example.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
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

    @MockitoBean
    private PredictionRepository predictionRepository;

    @MockitoBean
    private GameMetadataRepository gameMetadataRepository;

    @MockitoBean
    private GameInningScoreRepository gameInningScoreRepository;

    @MockitoBean
    private GameSummaryRepository gameSummaryRepository;

    @MockitoBean
    private VoteFinalResultRepository voteFinalResultRepository;

    @MockitoBean
    private UserRepository userRepository;

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
        gameRepository.deleteAll();
        Mockito.reset(
                predictionRepository,
                gameMetadataRepository,
                gameInningScoreRepository,
                gameSummaryRepository,
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
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
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

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(3);
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
        return gameRepository.save(GameEntity.builder()
                .gameId(gameId)
                .gameDate(gameDate)
                .stadium("잠실")
                .homeTeam("LG")
                .awayTeam("HH")
                .homeScore(homeScore)
                .awayScore(awayScore)
                .seasonId(seasonId)
                .gameStatus("COMPLETED")
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
    }
}
