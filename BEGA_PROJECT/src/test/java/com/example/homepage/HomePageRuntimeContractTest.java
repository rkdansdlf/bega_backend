package com.example.homepage;

import com.example.cheerboard.service.CheerService;
import com.example.common.exception.GlobalExceptionHandler;
import com.example.homepage.port.FeaturedMateQuery;
import com.example.kbo.repository.GameRepository;
import com.example.kbo.repository.MatchRangeProjection;
import com.example.kbo.service.LeagueStageResolver;
import com.example.kbo.validation.BaseballDataIntegrityGuard;
import com.example.kbo.validation.ManualBaseballDataMissingItem;
import com.example.kbo.validation.ManualBaseballDataRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HomePageRuntimeContractTest {

    private static final LocalDate SELECTED_DATE = LocalDate.of(2026, 6, 26);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    void leagueStartDatesReturns409WhenRequiredInternalEvidenceIsMissing() throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            fixture.removeRegularSeasonEvidence();

            assertManualDataRequired(fixture.mvc.perform(get("/api/kbo/league-start-dates")));
        }
    }

    @TestFactory
    Stream<DynamicTest> bootstrapReturns409ForMissingRequiredLeagueThroughActualFacade() {
        return Stream.of(false, true).map(completed -> dynamicTest("completed=" + completed, () -> {
            try (Fixture fixture = new Fixture(completed)) {
                fixture.removeRegularSeasonEvidence();

                assertManualDataRequired(fixture.bootstrap());
            }
        }));
    }

    @Test
    void scheduleAndBootstrapKeep200EmptyGamesWhenSeasonEvidenceExists() throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            fixture.mvc.perform(get("/api/kbo/schedule").param("date", SELECTED_DATE.toString()))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[]"));

            fixture.bootstrap()
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.selectedDate").value(SELECTED_DATE.toString()))
                    .andExpect(jsonPath("$.leagueStartDates.regularSeasonStart").value(fixture.regularStart.toString()))
                    .andExpect(jsonPath("$.leagueStartDates.postseasonStart").value(nullValue()))
                    .andExpect(jsonPath("$.leagueStartDates.koreanSeriesStart").value(nullValue()))
                    .andExpect(jsonPath("$.navigation.prevGameDate").value(nullValue()))
                    .andExpect(jsonPath("$.navigation.nextGameDate").value(nullValue()))
                    .andExpect(jsonPath("$.navigation.hasPrev").value(false))
                    .andExpect(jsonPath("$.navigation.hasNext").value(false))
                    .andExpect(jsonPath("$.games").isEmpty())
                    .andExpect(jsonPath("$.scheduledGamesWindow").isEmpty())
                    .andExpect(jsonPath("$.loadState.isFallback").value(false))
                    .andExpect(jsonPath("$.loadState.timedOut").value(false))
                    .andExpect(jsonPath("$.loadState.failedSections").isEmpty())
                    .andExpect(jsonPath("$.loadState.failureReason").value(nullValue()))
                    .andExpect(jsonPath("$.loadState.manualDataRequest").value(nullValue()));
        }
    }

    @Test
    void optionalLeagueNavigationKeeps200WithNullResolvedDate() throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            fixture.mvc.perform(get("/api/home/navigation")
                            .param("date", SELECTED_DATE.toString())
                            .param("scope", "postseason")
                            .param("seasonYear", "2026"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resolvedDate").value(nullValue()))
                    .andExpect(jsonPath("$.prevGameDate").value(nullValue()))
                    .andExpect(jsonPath("$.nextGameDate").value(nullValue()))
                    .andExpect(jsonPath("$.hasPrev").value(false))
                    .andExpect(jsonPath("$.hasNext").value(false));
        }
    }

    @Test
    void bootstrapKeeps200AndValidGamesWhenAnotherRowRequiresManualData() throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            // Reuse the explicitly mocked rows from HomePageGameServiceTest.
            MatchRangeProjection missingScoreGame = mock(MatchRangeProjection.class);
            MatchRangeProjection completedGame = mock(MatchRangeProjection.class);
            when(completedGame.getGameId()).thenReturn("20260626LGKT0");
            when(completedGame.getGameDate()).thenReturn(SELECTED_DATE);
            when(completedGame.getStadium()).thenReturn("수원");
            when(completedGame.getHomeTeam()).thenReturn("KT");
            when(completedGame.getAwayTeam()).thenReturn("LG");
            when(completedGame.getHomeScore()).thenReturn(2);
            when(completedGame.getAwayScore()).thenReturn(5);
            when(completedGame.getSeasonId()).thenReturn(2026);
            when(completedGame.getRawLeagueTypeCode()).thenReturn(0);
            when(completedGame.getGameStatus()).thenReturn("COMPLETED");
            when(completedGame.getStartTime()).thenReturn(LocalTime.of(18, 30));
            ManualBaseballDataRequest request = new ManualBaseballDataRequest(
                    "home.schedule",
                    List.of(new ManualBaseballDataMissingItem(
                            "final_score", "최종 점수", "과거 경기의 최종 점수가 비어 있습니다.", "home_score, away_score")),
                    "다음 야구 데이터가 필요합니다: 경기 ID=20260626HHSK0",
                    true);
            when(fixture.gameRepository.findCanonicalRangeProjectionByGameDate(eq(SELECTED_DATE), anyList()))
                    .thenReturn(List.of(missingScoreGame, completedGame));
            when(fixture.integrityGuard.findMatchProjectionManualDataRequest("home.schedule", missingScoreGame))
                    .thenReturn(request);

            fixture.bootstrap()
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.leagueStartDates.regularSeasonStart").value(fixture.regularStart.toString()))
                    .andExpect(jsonPath("$.games.length()").value(1))
                    .andExpect(jsonPath("$.games[0].gameId").value("20260626LGKT0"))
                    .andExpect(jsonPath("$.games[0].gameStatus").value("COMPLETED"))
                    .andExpect(jsonPath("$.games[0].homeScore").value(2))
                    .andExpect(jsonPath("$.games[0].awayScore").value(5))
                    .andExpect(jsonPath("$.loadState.isFallback").value(false))
                    .andExpect(jsonPath("$.loadState.failureReason").value("manual-data-required"))
                    .andExpect(jsonPath("$.loadState.manualDataRequest.scope").value("home.schedule"))
                    .andExpect(jsonPath("$.loadState.manualDataRequest.missingItems[0].key").value("final_score"));
        }
    }

    @TestFactory
    Stream<DynamicTest> bootstrapPropagatesDatabaseErrorsThroughActualFacadeAndController() {
        return Stream.of(false, true).flatMap(completed ->
                Stream.of("leagueStartDates", "navigation", "games", "scheduledGamesWindow").flatMap(section ->
                        Stream.of("sql", "transient", "connection").map(error ->
                                dynamicTest(section + "/" + error + "/completed=" + completed, () -> {
                                    try (Fixture fixture = new Fixture(completed)) {
                                        fixture.failBootstrapSection(section, databaseFailure(error));

                                        assertDatabaseError(fixture.bootstrap(), error);
                                    }
                                }))));
    }

    @TestFactory
    Stream<DynamicTest> wrappedDatabaseErrorsRetainExistingHttpContracts() {
        return Stream.of("sql", "transient", "connection").map(error -> dynamicTest(error, () -> {
            try (Fixture fixture = new Fixture(false)) {
                fixture.failBootstrapSection("games", new CompletionException(databaseFailure(error)));

                assertDatabaseError(fixture.bootstrap(), error);
            }
        }));
    }

    @TestFactory
    Stream<DynamicTest> widgetsPropagateDatabaseErrorsThroughActualFacadeAndController() {
        return Stream.of(false, true).flatMap(completed ->
                Stream.of("hotCheerPosts", "featuredMates", "rankingSnapshot").flatMap(section ->
                        Stream.of("sql", "connection").map(error ->
                                dynamicTest(section + "/" + error + "/completed=" + completed, () -> {
                                    try (Fixture fixture = new Fixture(completed)) {
                                        RuntimeException failure = databaseFailure(error);
                                        switch (section) {
                                            case "hotCheerPosts" -> when(fixture.cheerService.getHotPostsPublic(any(), eq("HYBRID")))
                                                    .thenThrow(failure);
                                            case "featuredMates" -> when(fixture.featuredMateQuery.getFeaturedMateCards(SELECTED_DATE, 4))
                                                    .thenThrow(failure);
                                            case "rankingSnapshot" -> fixture.failRankings(failure);
                                            default -> throw new AssertionError(section);
                                        }

                                        assertDatabaseError(fixture.mvc.perform(get("/api/home/widgets")
                                                .param("date", SELECTED_DATE.toString())
                                                .param("seasonYear", "2026")), error);
                                    }
                                }))));
    }

    @TestFactory
    Stream<DynamicTest> rankingSnapshotPropagatesDatabaseErrorsThroughActualFacade() {
        return Stream.of("sql", "connection").map(error -> dynamicTest(error, () -> {
            try (Fixture fixture = new Fixture(false)) {
                fixture.failRankings(databaseFailure(error));

                assertDatabaseError(fixture.mvc.perform(get("/api/kbo/rankings/snapshot")
                        .param("date", SELECTED_DATE.toString()).param("seasonYear", "2026")), error);
            }
        }));
    }

    @TestFactory
    Stream<DynamicTest> navigationPropagatesDatabaseErrorsThroughActualController() {
        return Stream.of("sql", "transient", "connection").map(error -> dynamicTest(error, () -> {
            try (Fixture fixture = new Fixture(false)) {
                when(fixture.gameRepository.findScopedGameDateOnOrAfter(eq(SELECTED_DATE), eq(2026), anyList(), anyList()))
                        .thenThrow(databaseFailure(error));

                assertDatabaseError(fixture.mvc.perform(get("/api/home/navigation")
                        .param("date", SELECTED_DATE.toString()).param("seasonYear", "2026")), error);
            }
        }));
    }

    @Test
    void widgetsReturns409WhenAutomaticRankingRequiresMissingLeagueEvidence() throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            fixture.removeRegularSeasonEvidence();

            assertManualDataRequired(fixture.mvc.perform(get("/api/home/widgets")
                    .param("date", SELECTED_DATE.toString())));
        }
    }

    private static void assertManualDataRequired(ResultActions response) throws Exception {
        response.andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MANUAL_BASEBALL_DATA_REQUIRED"))
                .andExpect(jsonPath("$.data.blocking").value(true))
                .andExpect(jsonPath("$.data.scope").isNotEmpty())
                .andExpect(jsonPath("$.data.missingItems").isNotEmpty())
                .andExpect(jsonPath("$.data.missingItems[0].key").isNotEmpty())
                .andExpect(jsonPath("$.data.missingItems[0].label").isNotEmpty())
                .andExpect(jsonPath("$.data.missingItems[0].reason").isNotEmpty())
                .andExpect(jsonPath("$.data.missingItems[0].expected_format").isNotEmpty())
                .andExpect(jsonPath("$.data.operatorMessage").isNotEmpty())
                .andExpect(jsonPath("$.leagueStartDates").doesNotExist());
    }

    private static void assertDatabaseError(ResultActions response, String error) throws Exception {
        response.andExpect(status().is("sql".equals(error) ? 500 : 503))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("sql".equals(error)
                        ? "INTERNAL_SERVER_ERROR" : "TEMPORARY_DATABASE_ERROR"))
                .andExpect(content().string(not(containsString("internal-db-detail"))));
    }

    private static RuntimeException databaseFailure(String error) {
        return switch (error) {
            case "sql" -> new BadSqlGrammarException("home read", "SELECT restricted_column FROM game",
                    new SQLException("internal-db-detail: permission denied", "42501"));
            case "transient" -> new TransientDataAccessResourceException("internal-db-detail: temporary failure");
            case "connection" -> new CannotGetJdbcConnectionException("internal-db-detail: connection unavailable");
            default -> throw new AssertionError(error);
        };
    }

    private static final class Fixture implements AutoCloseable {
        private final GameRepository gameRepository = mock(GameRepository.class);
        private final BaseballDataIntegrityGuard integrityGuard = mock(BaseballDataIntegrityGuard.class);
        private final CheerService cheerService = mock(CheerService.class);
        private final FeaturedMateQuery featuredMateQuery = mock(FeaturedMateQuery.class);
        private final LocalDate regularStart = LocalDate.of(LocalDate.now().getYear(), 3, 28);
        private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        private final HomePageFacadeService facade;
        private final MockMvc mvc;

        private Fixture(boolean completedBeforeAwait) throws Exception {
            DataSource dataSource = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            PreparedStatement statement = mock(PreparedStatement.class);
            ResultSet emptyRows = mock(ResultSet.class);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(statement);
            when(statement.executeQuery()).thenReturn(emptyRows);
            when(gameRepository.findConfiguredStartDateByTypeFromSeasonYear(0, regularStart.getYear()))
                    .thenReturn(Optional.of(regularStart));
            when(cheerService.getHotPostsPublic(any(), eq("HYBRID"))).thenReturn(Page.empty());
            HomePageGameService gameService = new HomePageGameService(
                    gameRepository, mock(HomePageTeamRepository.class), dataSource,
                    new LeagueStageResolver(gameRepository), integrityGuard);
            ExecutorService executor = completedBeforeAwait ? completedExecutor() : Executors.newVirtualThreadPerTaskExecutor();
            Duration timeout = Duration.ofSeconds(6);
            facade = new HomePageFacadeService(gameService, cheerService, featuredMateQuery,
                    null, null, timeout, timeout, FIXED_CLOCK, executor, meterRegistry);
            mvc = MockMvcBuilders.standaloneSetup(
                            new HomeController(facade, gameService, meterRegistry),
                            new HomePageController(gameService, facade))
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .build();
        }

        private static ExecutorService completedExecutor() {
            ExecutorService executor = mock(ExecutorService.class);
            when(executor.submit(org.mockito.ArgumentMatchers.<Callable<Object>>any())).thenAnswer(invocation -> {
                Callable<Object> callable = invocation.getArgument(0);
                FutureTask<Object> task = new FutureTask<>(callable);
                task.run();
                return task;
            });
            return executor;
        }

        private void removeRegularSeasonEvidence() {
            when(gameRepository.findConfiguredStartDateByTypeFromSeasonYear(0, regularStart.getYear()))
                    .thenReturn(Optional.empty());
        }

        private void failBootstrapSection(String section, RuntimeException failure) {
            switch (section) {
                case "leagueStartDates" -> when(gameRepository.findConfiguredStartDateByTypeFromSeasonYear(0, regularStart.getYear()))
                        .thenThrow(failure);
                case "navigation" -> when(gameRepository.findPrevGameDate(SELECTED_DATE)).thenThrow(failure);
                case "games" -> when(gameRepository.findCanonicalRangeProjectionByGameDate(eq(SELECTED_DATE), anyList()))
                        .thenThrow(failure);
                case "scheduledGamesWindow" -> when(gameRepository.findScheduledWindowProjectionByDateRange(
                        eq(SELECTED_DATE), eq(SELECTED_DATE.plusDays(7)), anyCollection(), anyList())).thenThrow(failure);
                default -> throw new AssertionError(section);
            }
        }

        private void failRankings(RuntimeException failure) {
            when(gameRepository.findTeamRankingsBySeasonFast(eq(2026), any(LocalDate.class), any(LocalDate.class)))
                    .thenThrow(failure);
        }

        private ResultActions bootstrap() throws Exception {
            return mvc.perform(get("/api/home/bootstrap").param("date", SELECTED_DATE.toString()));
        }

        @Override
        public void close() {
            facade.shutdownSectionExecutor();
            meterRegistry.close();
        }
    }
}
