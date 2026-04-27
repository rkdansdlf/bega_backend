package com.example.prediction.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.example.auth.repository.UserRepository;
import com.example.common.config.CacheConfig;
import com.example.common.exception.ConflictBusinessException;
import com.example.homepage.HomePageTeamRepository;
import com.example.kbo.repository.GameRepository;
import com.example.prediction.RankingPrediction;
import com.example.prediction.RankingPredictionRepository;
import com.example.prediction.RankingPredictionRequestDto;
import com.example.prediction.RankingPredictionResponseDto;
import com.example.prediction.RankingPredictionService;
import com.example.prediction.SeasonUtils;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import({
        RankingPredictionService.class,
        RankingPredictionConcurrencyIntegrationTest.TransactionAliasConfig.class
})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ranking_prediction_concurrency;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RankingPredictionConcurrencyIntegrationTest {

    private static final int TEST_SEASON_YEAR = 2026;
    private static final String TEST_USER_ID = "7";
    private static final List<String> TEST_TEAM_IDS = List.of(
            "LG", "DB", "SSG", "KT", "KH", "NC", "SS", "LT", "KIA", "HH");

    @Autowired
    private RankingPredictionService rankingPredictionService;

    @Autowired
    private RankingPredictionRepository rankingPredictionRepository;

    @Autowired
    private DataSource dataSource;

    @MockitoBean
    private GameRepository gameRepository;

    @MockitoBean
    private HomePageTeamRepository homePageTeamRepository;

    @MockitoBean
    private UserRepository userRepository;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        rankingPredictionRepository.deleteAll();
        ensureUniqueConstraint();

        when(gameRepository.findTeamRankingsBySeason(anyInt())).thenReturn(List.of());
        when(homePageTeamRepository.findAll()).thenReturn(List.of());
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        rankingPredictionRepository.deleteAll();
    }

    @Test
    @DisplayName("concurrent ranking prediction save persists one row and rejects the duplicate")
    void savePrediction_concurrentRequestsPersistOneRowAndRejectDuplicate() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            Callable<Object> task = () -> {
                readyLatch.countDown();
                if (!startLatch.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("timed out while waiting to start concurrent save");
                }
                try {
                    return savePredictionWithMockedSeason();
                } catch (Exception ex) {
                    return ex;
                }
            };

            Future<Object> first = executorService.submit(task);
            Future<Object> second = executorService.submit(task);

            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();

            Object firstResult = first.get(10, TimeUnit.SECONDS);
            Object secondResult = second.get(10, TimeUnit.SECONDS);

            List<Object> results = List.of(firstResult, secondResult);
            long successCount = results.stream()
                    .filter(RankingPredictionResponseDto.class::isInstance)
                    .count();
            long conflictCount = results.stream()
                    .filter(ConflictBusinessException.class::isInstance)
                    .count();

            assertThat(successCount).isEqualTo(1);
            assertThat(conflictCount).isEqualTo(1);

            List<RankingPrediction> savedPredictions = rankingPredictionRepository.findAll();
            assertThat(savedPredictions).hasSize(1);
            assertThat(savedPredictions.get(0).getUserId()).isEqualTo(TEST_USER_ID);
            assertThat(savedPredictions.get(0).getSeasonYear()).isEqualTo(TEST_SEASON_YEAR);
            assertThat(savedPredictions.get(0).getPredictionData()).containsExactlyElementsOf(TEST_TEAM_IDS);
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private RankingPredictionResponseDto savePredictionWithMockedSeason() {
        try (MockedStatic<SeasonUtils> seasonUtils = Mockito.mockStatic(SeasonUtils.class)) {
            seasonUtils.when(SeasonUtils::isPredictionPeriod).thenReturn(true);
            seasonUtils.when(SeasonUtils::getCurrentPredictionSeason).thenReturn(TEST_SEASON_YEAR);

            RankingPredictionRequestDto request = new RankingPredictionRequestDto();
            request.setSeasonYear(TEST_SEASON_YEAR);
            request.setTeamIdsInOrder(TEST_TEAM_IDS);
            return rankingPredictionService.savePrediction(request, TEST_USER_ID);
        }
    }

    private void ensureUniqueConstraint() {
        try {
            jdbcTemplate.execute("""
                    ALTER TABLE ranking_predictions
                    ADD CONSTRAINT uk_rank_pred_user_season UNIQUE (user_id, season_year)
                    """);
        } catch (DataAccessException ex) {
            String message = ex.getMostSpecificCause() != null
                    ? ex.getMostSpecificCause().getMessage()
                    : ex.getMessage();
            if (message == null || !message.toLowerCase().contains("uk_rank_pred_user_season")) {
                throw ex;
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TransactionAliasConfig {

        @Bean(name = {"transactionManager", "kboGameTransactionManager"})
        PlatformTransactionManager rankingPredictionConcurrencyTransactionManager(
                EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(
                    CacheConfig.RANKING_PREDICTION_CONTEXT,
                    CacheConfig.RANKING_SHARE_IDS);
        }
    }
}
