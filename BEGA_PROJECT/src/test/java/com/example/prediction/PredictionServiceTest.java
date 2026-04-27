package com.example.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.common.config.CacheConfig;
import com.example.common.exception.NotFoundBusinessException;
import com.example.kbo.entity.GameEntity;
import com.example.kbo.entity.GameInningScoreEntity;
import com.example.kbo.entity.GameMetadataEntity;
import com.example.kbo.entity.GameSummaryEntity;
import com.example.kbo.repository.CanonicalAdjacentGameDatesProjection;
import com.example.kbo.repository.CanonicalGameDateBoundsProjection;
import com.example.kbo.repository.GameDetailHeaderProjection;
import com.example.kbo.repository.GameRepository;
import com.example.kbo.repository.GameInningScoreRepository;
import com.example.kbo.repository.GameMetadataRepository;
import com.example.kbo.repository.MatchRangeProjection;
import com.example.kbo.repository.PredictionStatsGameProjection;
import com.example.kbo.repository.GameSummaryRepository;
import com.example.kbo.service.LeagueStageResolver;
import com.example.kbo.validation.BaseballDataIntegrityGuard;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class PredictionServiceTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private PredictionRepository predictionRepository;

    @Mock
    private GameMetadataRepository gameMetadataRepository;

    @Mock
    private GameInningScoreRepository gameInningScoreRepository;

    @Mock
    private GameSummaryRepository gameSummaryRepository;

    @Mock
    private VoteFinalResultRepository voteFinalResultRepository;

    @Mock
    private UserRepository userRepository;

    private PredictionService predictionService;

    @BeforeEach
    void setUp() {
        LeagueStageResolver leagueStageResolver = new LeagueStageResolver(gameRepository);
        BaseballDataIntegrityGuard baseballDataIntegrityGuard = mock(BaseballDataIntegrityGuard.class);
        CacheManager cacheManager = new ConcurrentMapCacheManager(CacheConfig.PREDICTION_VOTE_STATUS);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        predictionService = new PredictionService(
                predictionRepository,
                gameRepository,
                gameMetadataRepository,
                gameInningScoreRepository,
                gameSummaryRepository,
                voteFinalResultRepository,
                userRepository,
                leagueStageResolver,
                baseballDataIntegrityGuard,
                cacheManager,
                transactionManager
        );
        lenient().when(gameRepository.findConfiguredStartDateByTypeFromSeasonYear(anyInt(), anyInt()))
                .thenReturn(Optional.empty());
        lenient().when(gameRepository.findFirstStartDateByTypeFromSeasonYear(anyInt(), anyInt()))
                .thenReturn(Optional.empty());
        lenient().when(gameRepository.findLeagueTypeCodeBySeasonId(anyInt()))
                .thenReturn(Optional.empty());
        lenient().when(gameMetadataRepository.findByGameId(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());
    }

    @Test
    void getMatchesByDateRangeShouldReturnEmptyWhenCanonicalMatchesAreEmpty() {
        LocalDate startDate = LocalDate.of(2026, 2, 1);
        LocalDate endDate = LocalDate.of(2026, 2, 28);

        when(gameRepository.findCanonicalRangeProjectionByDateRangeNoCount(
                any(LocalDate.class),
                any(LocalDate.class),
                anyList(),
                any(Pageable.class)
        )).thenReturn(List.of());

        List<MatchDto> matches = predictionService.getMatchesByDateRange(startDate, endDate);

        assertThat(matches).isEmpty();
    }

    @Test
    void getMatchesByDateRangeShouldKeepCanonicalFilteringWhenCanonicalMatchesExist() {
        LocalDate startDate = LocalDate.of(2026, 2, 1);
        LocalDate endDate = LocalDate.of(2026, 2, 28);
        MatchRangeProjection canonical = buildRangeMatch("202602010002", startDate, "HH", "SS", null, null, null);

        when(gameRepository.findCanonicalRangeProjectionByDateRangeNoCount(
                any(LocalDate.class),
                any(LocalDate.class),
                anyList(),
                any(Pageable.class)
        )).thenReturn(List.of(canonical));

        List<MatchDto> matches = predictionService.getMatchesByDateRange(startDate, endDate);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getGameId()).isEqualTo("202602010002");
    }

    @Test
    void getMatchDayNavigationShouldExposeResolvedGameStatusForListItems() {
        LocalDate targetDate = LocalDate.now().minusDays(1);
        MatchRangeProjection staleScheduled = buildRangeMatch("202603290001", targetDate, "HH", "SS", 20260, 0, null);
        when(staleScheduled.getHomeScore()).thenReturn(4);
        when(staleScheduled.getAwayScore()).thenReturn(2);
        when(staleScheduled.getGameStatus()).thenReturn("SCHEDULED");
        when(staleScheduled.getStartTime()).thenReturn(LocalTime.of(14, 0));
        when(gameRepository.findCanonicalRangeProjectionByGameDate(
                any(LocalDate.class),
                anyList()
        )).thenReturn(List.of(staleScheduled));
        when(gameRepository.findCanonicalAdjacentGameDates(any(LocalDate.class), anyList()))
                .thenReturn(null);

        MatchDayNavigationResponseDto response = predictionService.getMatchDayNavigation(targetDate);

        assertThat(response.getGames()).hasSize(1);
        MatchDto match = response.getGames().get(0);
        assertThat(match.getGameStatus()).isEqualTo("COMPLETED");
        assertThat(match.getStartTime()).isEqualTo(LocalTime.of(14, 0));
    }

    @Test
    void getMatchesByDateRangeShouldPopulateLeagueTypeAndSeriesMetadataFromSeason() {
        LocalDate startDate = LocalDate.of(2025, 10, 20);
        LocalDate endDate = LocalDate.of(2025, 10, 20);
        MatchRangeProjection postseasonGame = buildRangeMatch(
                "202510200002",
                startDate,
                "HH",
                "LG",
                20254,
                4,
                3);

        when(gameRepository.findCanonicalRangeProjectionByDateRangeNoCount(
                any(LocalDate.class),
                any(LocalDate.class),
                anyList(),
                any(Pageable.class)
        )).thenReturn(List.of(postseasonGame));
        when(gameRepository.findConfiguredStartDateByTypeFromSeasonYear(4, 2025))
                .thenReturn(Optional.of(LocalDate.of(2025, 10, 18)));

        List<MatchDto> matches = predictionService.getMatchesByDateRange(startDate, endDate);

        assertThat(matches).hasSize(1);
        MatchDto match = matches.get(0);
        assertThat(match.getLeagueType()).isEqualTo("POST");
        assertThat(match.getPostSeasonSeries()).isEqualTo("PO");
        assertThat(match.getSeriesGameNo()).isEqualTo(3);
    }

    @Test
    void getMatchesByDateRangeShouldInferPostseasonRoundFromConfiguredStartDatesWhenSeasonTypeIsInflated() {
        LocalDate gameDate = LocalDate.of(2025, 10, 18);
        MatchRangeProjection postseasonGame = buildRangeMatch(
                "202510180002",
                gameDate,
                "SS",
                "HH",
                264,
                5,
                2);

        when(gameRepository.findCanonicalRangeProjectionByDateRangeNoCount(
                any(LocalDate.class),
                any(LocalDate.class),
                anyList(),
                any(Pageable.class)
        )).thenReturn(List.of(postseasonGame));
        when(gameRepository.findConfiguredStartDateByTypeFromSeasonYear(2, 2025))
                .thenReturn(Optional.of(LocalDate.of(2025, 10, 6)));
        when(gameRepository.findConfiguredStartDateByTypeFromSeasonYear(3, 2025))
                .thenReturn(Optional.of(LocalDate.of(2025, 10, 9)));
        when(gameRepository.findConfiguredStartDateByTypeFromSeasonYear(4, 2025))
                .thenReturn(Optional.of(LocalDate.of(2025, 10, 18)));
        when(gameRepository.findConfiguredStartDateByTypeFromSeasonYear(5, 2025))
                .thenReturn(Optional.of(LocalDate.of(2025, 10, 26)));

        List<MatchDto> matches = predictionService.getMatchesByDateRange(gameDate, gameDate);

        assertThat(matches).hasSize(1);
        MatchDto match = matches.get(0);
        assertThat(match.getLeagueType()).isEqualTo("POST");
        assertThat(match.getPostSeasonSeries()).isEqualTo("PO");
        assertThat(match.getSeriesGameNo()).isEqualTo(2);
    }

    @Test
    @DisplayName("getMatchesByDateRange keeps stable seriesGameNo when page starts mid-series")
    void getMatchesByDateRange_keepsStableSeriesGameNoWhenPageStartsMidSeries() {
        LocalDate gameDate = LocalDate.of(2025, 10, 18);
        MatchRangeProjection third = buildRangeMatch(
                "202510200001",
                gameDate.plusDays(2),
                "HH",
                "LG",
                264,
                4,
                3);

        when(gameRepository.findCanonicalRangeProjectionByDateRangeNoCount(
                any(LocalDate.class),
                any(LocalDate.class),
                anyList(),
                any(Pageable.class)
        )).thenReturn(List.of(third));
        when(gameRepository.findConfiguredStartDateByTypeFromSeasonYear(anyInt(), anyInt()))
                .thenReturn(Optional.empty());

        List<MatchDto> matches = predictionService.getMatchesByDateRange(
                gameDate,
                gameDate.plusDays(2),
                true,
                1,
                1);

        assertThat(matches).extracting(MatchDto::getSeriesGameNo).containsExactly(3);
    }

    @Test
    void getMatchBoundsShouldReturnBoundsWhenDataExists() {
        LocalDate earliest = LocalDate.of(2026, 3, 20);
        LocalDate latest = LocalDate.of(2026, 10, 1);
        CanonicalGameDateBoundsProjection bounds = mock(CanonicalGameDateBoundsProjection.class);

        when(bounds.getEarliestGameDate()).thenReturn(earliest);
        when(bounds.getLatestGameDate()).thenReturn(latest);
        when(gameRepository.findCanonicalGameDateBounds(anyList())).thenReturn(bounds);

        MatchBoundsResponseDto response = predictionService.getMatchBounds();

        assertThat(response.isHasData()).isTrue();
        assertThat(response.getEarliestGameDate()).isEqualTo(earliest);
        assertThat(response.getLatestGameDate()).isEqualTo(latest);
    }

    @Test
    void getMatchBoundsShouldReturnEmptyWhenNoDataExists() {
        CanonicalGameDateBoundsProjection bounds = mock(CanonicalGameDateBoundsProjection.class);
        when(bounds.getEarliestGameDate()).thenReturn(null);
        when(bounds.getLatestGameDate()).thenReturn(null);
        when(gameRepository.findCanonicalGameDateBounds(anyList())).thenReturn(bounds);

        MatchBoundsResponseDto response = predictionService.getMatchBounds();

        assertThat(response.isHasData()).isFalse();
        assertThat(response.getEarliestGameDate()).isNull();
        assertThat(response.getLatestGameDate()).isNull();
    }

    @Test
    void getMatchesByDateShouldUseProjectionQueryWithoutSeriesCountFallback() {
        LocalDate targetDate = LocalDate.of(2026, 5, 5);
        MatchRangeProjection canonical = buildRangeMatch("202605050001", targetDate, "HH", "SS", 20260, 0, null);

        when(gameRepository.findCanonicalRangeProjectionByGameDate(
                org.mockito.ArgumentMatchers.eq(targetDate),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(canonical));

        List<MatchDto> matches = predictionService.getMatchesByDate(targetDate);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getGameId()).isEqualTo("202605050001");
        verify(gameRepository, never()).findByGameDate(targetDate);
        verify(gameRepository, never()).findConfiguredStartDateByTypeFromSeasonYear(anyInt(), anyInt());
        verify(gameRepository, never()).findFirstStartDateByTypeFromSeasonYear(anyInt(), anyInt());
    }

    @Test
    void getMatchesByDateShouldUpgradeToKoreanSeriesUsingActualFirstGameDateWhenConfiguredStartMissing() {
        LocalDate targetDate = LocalDate.of(2025, 10, 29);
        MatchRangeProjection postseason = buildRangeMatch("202510290001", targetDate, "HH", "LG", 20254, 4, 3);

        when(gameRepository.findCanonicalRangeProjectionByGameDate(
                org.mockito.ArgumentMatchers.eq(targetDate),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(postseason));
        when(gameRepository.findFirstStartDateByTypeFromSeasonYear(5, 2025))
                .thenReturn(Optional.of(LocalDate.of(2025, 10, 26)));

        List<MatchDto> matches = predictionService.getMatchesByDate(targetDate);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getPostSeasonSeries()).isEqualTo("KS");
        assertThat(matches.get(0).getLeagueType()).isEqualTo("POST");
    }

    @Test
    void getMatchesByDateShouldDowngradeInflatedRawStageWhenHigherStageStartIsKnownFromActualGames() {
        LocalDate targetDate = LocalDate.of(2025, 10, 18);
        MatchRangeProjection inflated = buildRangeMatch("202510180001", targetDate, "HH", "LG", 20254, 5, 1);

        when(gameRepository.findCanonicalRangeProjectionByGameDate(
                org.mockito.ArgumentMatchers.eq(targetDate),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(inflated));
        when(gameRepository.findFirstStartDateByTypeFromSeasonYear(4, 2025))
                .thenReturn(Optional.of(LocalDate.of(2025, 10, 18)));
        when(gameRepository.findFirstStartDateByTypeFromSeasonYear(5, 2025))
                .thenReturn(Optional.of(LocalDate.of(2025, 10, 26)));

        List<MatchDto> matches = predictionService.getMatchesByDate(targetDate);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getPostSeasonSeries()).isEqualTo("PO");
    }

    @Test
    void getMatchesByDateShouldKeepRawStageWhenHigherStageStartIsUnknown() {
        LocalDate targetDate = LocalDate.of(2025, 10, 29);
        MatchRangeProjection rawKs = buildRangeMatch("202510290002", targetDate, "HH", "LG", 20255, 5, 3);

        when(gameRepository.findCanonicalRangeProjectionByGameDate(
                org.mockito.ArgumentMatchers.eq(targetDate),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(rawKs));
        when(gameRepository.findConfiguredStartDateByTypeFromSeasonYear(4, 2025))
                .thenReturn(Optional.of(LocalDate.of(2025, 10, 18)));

        List<MatchDto> matches = predictionService.getMatchesByDate(targetDate);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getPostSeasonSeries()).isEqualTo("KS");
    }

    @Test
    void getRecentCompletedGamesShouldUseProjectionQueryForRecentDates() {
        LocalDate today = LocalDate.now();
        MatchRangeProjection recent = buildRangeMatch("202605060001", today.minusDays(1), "HH", "SS", 20260, 0, null);

        when(gameRepository.findRecentDistinctGameDates(any(LocalDate.class)))
                .thenReturn(List.of(
                        today.minusDays(1),
                        today.minusDays(2),
                        today.minusDays(3),
                        today.minusDays(4),
                        today.minusDays(5),
                        today.minusDays(6),
                        today.minusDays(7),
                        today.minusDays(8)));
        when(gameRepository.findCanonicalCompletedRangeProjectionByGameDates(
                org.mockito.ArgumentMatchers.argThat(gameDates -> gameDates.size() == 7 && !gameDates.contains(today.minusDays(8))),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(recent));

        List<MatchDto> matches = predictionService.getRecentCompletedGames();

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getGameId()).isEqualTo("202605060001");
        verify(gameRepository, never()).findAllByGameDatesIn(anyList());
        verify(gameRepository, never()).findConfiguredStartDateByTypeFromSeasonYear(anyInt(), anyInt());
    }

    @Test
    void getMatchDayNavigationShouldReturnGamesAndAdjacentDates() {
        LocalDate targetDate = LocalDate.of(2026, 5, 5);
        LocalDate prevDate = LocalDate.of(2026, 5, 3);
        LocalDate nextDate = LocalDate.of(2026, 5, 7);
        MatchRangeProjection canonical = buildRangeMatch("202605050001", targetDate, "HH", "SS", 20260, 0, null);
        CanonicalAdjacentGameDatesProjection adjacentDates = mock(CanonicalAdjacentGameDatesProjection.class);

        when(gameRepository.findCanonicalRangeProjectionByGameDate(
                org.mockito.ArgumentMatchers.eq(targetDate),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(canonical));
        when(adjacentDates.getPrevDate()).thenReturn(prevDate);
        when(adjacentDates.getNextDate()).thenReturn(nextDate);
        when(gameRepository.findCanonicalAdjacentGameDates(
                org.mockito.ArgumentMatchers.eq(targetDate),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(adjacentDates);

        MatchDayNavigationResponseDto response = predictionService.getMatchDayNavigation(targetDate);

        assertThat(response.getDate()).isEqualTo(targetDate);
        assertThat(response.getGames()).hasSize(1);
        assertThat(response.getGames().get(0).getGameId()).isEqualTo("202605050001");
        assertThat(response.getPrevDate()).isEqualTo(prevDate);
        assertThat(response.getNextDate()).isEqualTo(nextDate);
        assertThat(response.isHasPrev()).isTrue();
        assertThat(response.isHasNext()).isTrue();
        verify(gameRepository, never()).findByGameDate(targetDate);
        verify(gameRepository, never()).findConfiguredStartDateByTypeFromSeasonYear(anyInt(), anyInt());
    }

    @Test
    void getMatchDayNavigationShouldKeepEmptyTodayWhileReturningAdjacentDates() {
        LocalDate targetDate = LocalDate.of(2026, 5, 5);
        LocalDate prevDate = LocalDate.of(2026, 5, 4);
        LocalDate nextDate = LocalDate.of(2026, 5, 6);
        CanonicalAdjacentGameDatesProjection adjacentDates = mock(CanonicalAdjacentGameDatesProjection.class);

        when(gameRepository.findCanonicalRangeProjectionByGameDate(
                org.mockito.ArgumentMatchers.eq(targetDate),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of());
        when(adjacentDates.getPrevDate()).thenReturn(prevDate);
        when(adjacentDates.getNextDate()).thenReturn(nextDate);
        when(gameRepository.findCanonicalAdjacentGameDates(
                org.mockito.ArgumentMatchers.eq(targetDate),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(adjacentDates);

        MatchDayNavigationResponseDto response = predictionService.getMatchDayNavigation(targetDate);

        assertThat(response.getDate()).isEqualTo(targetDate);
        assertThat(response.getGames()).isEmpty();
        assertThat(response.getPrevDate()).isEqualTo(prevDate);
        assertThat(response.getNextDate()).isEqualTo(nextDate);
        assertThat(response.isHasPrev()).isTrue();
        assertThat(response.isHasNext()).isTrue();
        verify(gameRepository, never()).findByGameDate(targetDate);
    }

    @Test
    @DisplayName("getMatchBounds는 legacy 팀코드 variant까지 포함해 조회한다")
    void getMatchBounds_includesLegacyTeamVariants() {
        LocalDate earliest = LocalDate.of(2026, 3, 22);
        LocalDate latest = LocalDate.of(2026, 10, 1);
        CanonicalGameDateBoundsProjection bounds = mock(CanonicalGameDateBoundsProjection.class);

        when(bounds.getEarliestGameDate()).thenReturn(earliest);
        when(bounds.getLatestGameDate()).thenReturn(latest);
        when(gameRepository.findCanonicalGameDateBounds(anyList())).thenReturn(bounds);

        predictionService.getMatchBounds();

        verify(gameRepository).findCanonicalGameDateBounds(org.mockito.ArgumentMatchers.argThat(teamCodes ->
                teamCodes.contains("SSG")
                        && teamCodes.contains("SK")
                        && teamCodes.contains("DB")
                        && teamCodes.contains("OB")
                        && teamCodes.contains("KH")
                        && teamCodes.contains("WO")
                        && teamCodes.contains("NX")));
    }

    @Test
    void getGameDetailShouldReturnHeaderMetadataAndCollections() {
        GameDetailHeaderProjection detailHeader = buildGameDetailHeader("202603200001", LocalDate.of(2026, 3, 20));
        GameInningScoreEntity inning = GameInningScoreEntity.builder()
                .gameId("202603200001")
                .inning(1)
                .teamSide("home")
                .teamCode("LG")
                .runs(2)
                .isExtra(false)
                .build();
        GameSummaryEntity summary = GameSummaryEntity.builder()
                .gameId("202603200001")
                .summaryType("HIGHLIGHT")
                .playerId(7)
                .playerName("홍길동")
                .detailText("결승타")
                .build();

        when(gameRepository.findGameDetailHeaderByGameId("202603200001")).thenReturn(Optional.of(detailHeader));
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc("202603200001"))
                .thenReturn(List.of(inning));
        when(gameSummaryRepository.findAllByGameIdOrderBySummaryTypeAscIdAsc("202603200001"))
                .thenReturn(List.of(summary));

        GameDetailDto response = predictionService.getGameDetail("202603200001");

        assertThat(response.getGameId()).isEqualTo("202603200001");
        assertThat(response.getStadiumName()).isEqualTo("잠실야구장");
        assertThat(response.getAttendance()).isEqualTo(12345);
        assertThat(response.getStartTime()).isEqualTo(LocalTime.of(18, 30));
        assertThat(response.getInningScores()).hasSize(1);
        assertThat(response.getInningScores().get(0).getRuns()).isEqualTo(2);
        assertThat(response.getSummary()).hasSize(1);
        assertThat(response.getSummary().get(0).getDetail()).isEqualTo("결승타");
        verify(gameRepository, never()).findByGameId("202603200001");
        verify(gameMetadataRepository, never()).findByGameId("202603200001");
    }

    @Test
    void getGameDetailShouldKeepMetadataFieldsNullWhenHeaderHasNoMetadata() {
        GameDetailHeaderProjection detailHeader = buildGameDetailHeaderWithoutMetadata("202603210001", LocalDate.of(2026, 3, 21));

        when(gameRepository.findGameDetailHeaderByGameId("202603210001")).thenReturn(Optional.of(detailHeader));
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc("202603210001"))
                .thenReturn(List.of());
        when(gameSummaryRepository.findAllByGameIdOrderBySummaryTypeAscIdAsc("202603210001"))
                .thenReturn(List.of());

        GameDetailDto response = predictionService.getGameDetail("202603210001");

        assertThat(response.getGameId()).isEqualTo("202603210001");
        assertThat(response.getStadiumName()).isNull();
        assertThat(response.getAttendance()).isNull();
        assertThat(response.getWeather()).isNull();
        assertThat(response.getGameTimeMinutes()).isNull();
        assertThat(response.getInningScores()).isEmpty();
        assertThat(response.getSummary()).isEmpty();
        verify(gameRepository, never()).findByGameId("202603210001");
        verify(gameMetadataRepository, never()).findByGameId("202603210001");
    }

    @Test
    void getGameDetailShouldThrowWhenHeaderIsMissing() {
        when(gameRepository.findGameDetailHeaderByGameId("MISSING")).thenReturn(Optional.empty());

        assertThrows(NotFoundBusinessException.class, () -> predictionService.getGameDetail("MISSING"));
        verify(gameInningScoreRepository, never()).findAllByGameIdOrderByInningAscTeamSideAsc(any());
        verify(gameSummaryRepository, never()).findAllByGameIdOrderBySummaryTypeAscIdAsc(any());
    }

    @Test
    void getVoteStatusShouldUseAggregatedCountsWhenFinalResultMissing() {
        PredictionVoteCountsProjection voteCounts = mock(PredictionVoteCountsProjection.class);

        when(voteFinalResultRepository.findById("202603200001")).thenReturn(Optional.empty());
        when(voteCounts.getHomeVotes()).thenReturn(7L);
        when(voteCounts.getAwayVotes()).thenReturn(3L);
        when(predictionRepository.findVoteCountsByGameId("202603200001")).thenReturn(voteCounts);

        PredictionResponseDto response = predictionService.getVoteStatus("202603200001");

        assertThat(response.getHomeVotes()).isEqualTo(7L);
        assertThat(response.getAwayVotes()).isEqualTo(3L);
        assertThat(response.getTotalVotes()).isEqualTo(10L);
        assertThat(response.getHomePercentage()).isEqualTo(70);
        assertThat(response.getAwayPercentage()).isEqualTo(30);
        verify(predictionRepository, never()).countByGameIdAndVotedTeam(any(), any());
        verify(predictionRepository, never()).countByGameId(any());
    }

    @Test
    void getVoteStatusShouldReuseCachedCountsAcrossRepeatedReads() {
        PredictionVoteCountsProjection voteCounts = mock(PredictionVoteCountsProjection.class);

        when(voteFinalResultRepository.findById("202603200001")).thenReturn(Optional.empty());
        when(voteCounts.getHomeVotes()).thenReturn(8L);
        when(voteCounts.getAwayVotes()).thenReturn(2L);
        when(predictionRepository.findVoteCountsByGameId("202603200001")).thenReturn(voteCounts);

        PredictionResponseDto first = predictionService.getVoteStatus("202603200001");
        PredictionResponseDto second = predictionService.getVoteStatus("202603200001");

        assertThat(first.getTotalVotes()).isEqualTo(10L);
        assertThat(second.getTotalVotes()).isEqualTo(10L);
        verify(voteFinalResultRepository, times(1)).findById("202603200001");
        verify(predictionRepository, times(1)).findVoteCountsByGameId("202603200001");
    }

    @Test
    void getVoteStatusShouldFallBackWhenCacheReadFails() {
        CacheManager cacheManager = mock(CacheManager.class);
        Cache cache = mock(Cache.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(cacheManager.getCache(CacheConfig.PREDICTION_VOTE_STATUS)).thenReturn(cache);
        when(cache.get("202603200001")).thenThrow(new SerializationException("broken cache payload"));
        PredictionVoteCountsProjection voteCounts = mock(PredictionVoteCountsProjection.class);
        when(voteFinalResultRepository.findById("202603200001")).thenReturn(Optional.empty());
        when(voteCounts.getHomeVotes()).thenReturn(6L);
        when(voteCounts.getAwayVotes()).thenReturn(4L);
        when(predictionRepository.findVoteCountsByGameId("202603200001")).thenReturn(voteCounts);

        PredictionService localService = new PredictionService(
                predictionRepository,
                gameRepository,
                gameMetadataRepository,
                gameInningScoreRepository,
                gameSummaryRepository,
                voteFinalResultRepository,
                userRepository,
                new LeagueStageResolver(gameRepository),
                mock(BaseballDataIntegrityGuard.class),
                cacheManager,
                transactionManager);

        PredictionResponseDto response = localService.getVoteStatus("202603200001");

        assertThat(response.getTotalVotes()).isEqualTo(10L);
        verify(cache).evict("202603200001");
        verify(predictionRepository).findVoteCountsByGameId("202603200001");
        verify(cache).put(eq("202603200001"), any(PredictionVoteStatusCacheEntry.class));
    }

    @Test
    void getVoteStatusShouldIgnoreCacheWriteFailure() {
        CacheManager cacheManager = mock(CacheManager.class);
        Cache cache = mock(Cache.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(cacheManager.getCache(CacheConfig.PREDICTION_VOTE_STATUS)).thenReturn(cache);
        when(cache.get("202603200001")).thenReturn(null);
        doThrow(new SerializationException("write failed")).when(cache)
                .put(eq("202603200001"), any(PredictionVoteStatusCacheEntry.class));
        PredictionVoteCountsProjection voteCounts = mock(PredictionVoteCountsProjection.class);
        when(voteFinalResultRepository.findById("202603200001")).thenReturn(Optional.empty());
        when(voteCounts.getHomeVotes()).thenReturn(5L);
        when(voteCounts.getAwayVotes()).thenReturn(5L);
        when(predictionRepository.findVoteCountsByGameId("202603200001")).thenReturn(voteCounts);

        PredictionService localService = new PredictionService(
                predictionRepository,
                gameRepository,
                gameMetadataRepository,
                gameInningScoreRepository,
                gameSummaryRepository,
                voteFinalResultRepository,
                userRepository,
                new LeagueStageResolver(gameRepository),
                mock(BaseballDataIntegrityGuard.class),
                cacheManager,
                transactionManager);

        PredictionResponseDto response = localService.getVoteStatus("202603200001");

        assertThat(response.getTotalVotes()).isEqualTo(10L);
        verify(cache).put(eq("202603200001"), any(PredictionVoteStatusCacheEntry.class));
        verify(cache).evict("202603200001");
    }

    @Test
    void getVoteStatusShouldPreferFinalResultWithoutPredictionCountQuery() {
        VoteFinalResult finalResult = VoteFinalResult.builder()
                .gameId("202603200001")
                .finalVotesA(11)
                .finalVotesB(9)
                .finalWinner("HOME")
                .build();

        when(voteFinalResultRepository.findById("202603200001")).thenReturn(Optional.of(finalResult));

        PredictionResponseDto response = predictionService.getVoteStatus("202603200001");

        assertThat(response.getHomeVotes()).isEqualTo(11L);
        assertThat(response.getAwayVotes()).isEqualTo(9L);
        assertThat(response.getTotalVotes()).isEqualTo(20L);
        assertThat(response.getHomePercentage()).isEqualTo(55);
        assertThat(response.getAwayPercentage()).isEqualTo(45);
        verify(predictionRepository, never()).findVoteCountsByGameId(any());
        verify(predictionRepository, never()).countByGameIdAndVotedTeam(any(), any());
    }

    // ========== vote() ==========

    @Test
    @DisplayName("vote - 존재하지 않는 경기에 투표하면 예외 발생")
    void vote_throwsIllegalArgumentWhenGameNotFound() {
        when(gameRepository.findByGameId("NOTEXIST")).thenReturn(Optional.empty());

        PredictionRequestDto request = new PredictionRequestDto();
        request.setGameId("NOTEXIST");
        request.setVotedTeam("home");

        assertThrows(IllegalArgumentException.class, () -> predictionService.vote(1L, request));
    }

    @Test
    @DisplayName("vote - 포인트가 0일 때 신규 투표 시 예외 발생")
    void vote_throwsWhenUserHasInsufficientPoints() {
        GameEntity game = buildCanonicalMockGame("202603200001");
        stubVoteWindowOpen(game, "202603200001");

        UserEntity user = mock(UserEntity.class);
        when(user.getCheerPoints()).thenReturn(0);

        when(gameRepository.findByGameId("202603200001")).thenReturn(Optional.of(game));
        when(userRepository.findByIdForWrite(1L)).thenReturn(Optional.of(user));
        when(predictionRepository.findByGameIdAndUserIdForWrite("202603200001", 1L))
                .thenReturn(Optional.empty());

        PredictionRequestDto request = new PredictionRequestDto();
        request.setGameId("202603200001");
        request.setVotedTeam("home");

        assertThrows(IllegalArgumentException.class, () -> predictionService.vote(1L, request));
    }

    @Test
    @DisplayName("vote - 같은 팀으로 재전송하면 기존 투표를 유지하고 삭제하지 않음")
    void vote_sameTeamResubmissionIsNoOp() {
        GameEntity game = buildCanonicalMockGame("202603200001");
        stubVoteWindowOpen(game, "202603200001");
        Prediction existingPrediction = mock(Prediction.class);

        when(existingPrediction.getVotedTeam()).thenReturn("home");
        when(gameRepository.findByGameId("202603200001")).thenReturn(Optional.of(game));
        when(predictionRepository.findByGameIdAndUserIdForWrite("202603200001", 1L))
                .thenReturn(Optional.of(existingPrediction));

        PredictionRequestDto request = new PredictionRequestDto();
        request.setGameId("202603200001");
        request.setVotedTeam("home");

        predictionService.vote(1L, request);

        verify(predictionRepository, never()).delete(existingPrediction);
        verify(predictionRepository, never()).saveAndFlush(any());
        verify(userRepository, never()).findByIdForWrite(any());
    }

    // ========== cancelVote() ==========

    @Test
    @DisplayName("cancelVote - 투표 내역이 없으면 IllegalStateException 발생")
    void cancelVote_throwsWhenNoPredictionFound() {
        GameEntity game = buildCanonicalMockGame("202603200001");
        stubVoteWindowOpen(game, "202603200001");

        when(gameRepository.findByGameId("202603200001")).thenReturn(Optional.of(game));
        when(predictionRepository.findByGameIdAndUserIdForWrite("202603200001", 1L))
                .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> predictionService.cancelVote(1L, "202603200001"));
    }

    @Test
    @DisplayName("cancelVote - 투표 레코드를 삭제하고 포인트는 환불하지 않음")
    void cancelVote_deletesVoteWithoutRefund() {
        GameEntity game = buildCanonicalMockGame("202603200001");
        stubVoteWindowOpen(game, "202603200001");
        Prediction prediction = mock(Prediction.class);

        when(gameRepository.findByGameId("202603200001")).thenReturn(Optional.of(game));
        when(predictionRepository.findByGameIdAndUserIdForWrite("202603200001", 1L))
                .thenReturn(Optional.of(prediction));

        predictionService.cancelVote(1L, "202603200001");

        verify(predictionRepository).delete(prediction);
        // userRepository should NOT be called (no refund)
        verify(userRepository, org.mockito.Mockito.never()).findByIdForWrite(any());
    }

    // ========== getUserStats() ==========

    @Test
    @DisplayName("getUserStats - 예측이 없으면 모든 통계가 0")
    void getUserStats_returnsZeroStatsWhenNoPredictions() {
        when(predictionRepository.findStatsRowsByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of());

        var stats = predictionService.getUserStats(1L);

        assertThat(stats.getTotalPredictions()).isEqualTo(0);
        assertThat(stats.getCorrectPredictions()).isEqualTo(0);
        assertThat(stats.getAccuracy()).isEqualTo(0.0);
        assertThat(stats.getStreak()).isEqualTo(0);
    }

    @Test
    @DisplayName("getUserStats - 완료된 경기에 대한 정확도와 연속 적중 계산")
    void getUserStats_calculatesAccuracyAndStreak() {
        // Predictions fetched in DESC order: p1=newest (correct), p2=oldest (wrong)
        PredictionStatsRowProjection p1 = mock(PredictionStatsRowProjection.class);
        PredictionStatsRowProjection p2 = mock(PredictionStatsRowProjection.class);
        when(predictionRepository.findStatsRowsByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(p1, p2));

        PredictionStatsGameProjection g1 = mock(PredictionStatsGameProjection.class);
        PredictionStatsGameProjection g2 = mock(PredictionStatsGameProjection.class);
        when(gameRepository.findPredictionStatsGameSummaries(eq(List.of("202603200001", "202603210001")), anyList()))
                .thenReturn(List.of(g1, g2));

        // p1: voted "home", winner "home" → correct
        when(p1.getGameId()).thenReturn("202603200001");
        when(p1.getVotedTeam()).thenReturn("home");
        when(g1.getWinner()).thenReturn("home");
        when(g1.getGameId()).thenReturn("202603200001");

        // p2: voted "home", winner "away" → wrong (breaks streak)
        when(p2.getGameId()).thenReturn("202603210001");
        when(p2.getVotedTeam()).thenReturn("home");
        when(g2.getWinner()).thenReturn("away");
        when(g2.getGameId()).thenReturn("202603210001");

        var stats = predictionService.getUserStats(1L);

        assertThat(stats.getTotalPredictions()).isEqualTo(2);
        assertThat(stats.getCorrectPredictions()).isEqualTo(1);
        assertThat(stats.getAccuracy()).isEqualTo(50.0);
        assertThat(stats.getStreak()).isEqualTo(1); // newest correct, then broken
    }

    @Test
    @DisplayName("getUserStats uses minimal projections instead of full prediction/game entities")
    void getUserStats_usesMinimalProjectionQueries() {
        PredictionStatsRowProjection firstPrediction = mock(PredictionStatsRowProjection.class);
        PredictionStatsRowProjection secondPrediction = mock(PredictionStatsRowProjection.class);
        PredictionStatsGameProjection firstGame = mock(PredictionStatsGameProjection.class);
        PredictionStatsGameProjection secondGame = mock(PredictionStatsGameProjection.class);

        when(predictionRepository.findStatsRowsByUserIdOrderByCreatedAtDesc(7L))
                .thenReturn(List.of(firstPrediction, secondPrediction));
        when(firstPrediction.getGameId()).thenReturn("202603200001");
        when(firstPrediction.getVotedTeam()).thenReturn("home");
        when(secondPrediction.getGameId()).thenReturn("202603210001");
        when(secondPrediction.getVotedTeam()).thenReturn("away");
        when(firstGame.getGameId()).thenReturn("202603200001");
        when(firstGame.getWinner()).thenReturn("home");
        when(secondGame.getGameId()).thenReturn("202603210001");
        when(secondGame.getWinner()).thenReturn("away");
        when(gameRepository.findPredictionStatsGameSummaries(eq(List.of("202603200001", "202603210001")), anyList()))
                .thenReturn(List.of(firstGame, secondGame));

        UserPredictionStatsDto stats = predictionService.getUserStats(7L);

        assertThat(stats.getTotalPredictions()).isEqualTo(2);
        assertThat(stats.getCorrectPredictions()).isEqualTo(2);
        assertThat(stats.getAccuracy()).isEqualTo(100.0);
        assertThat(stats.getStreak()).isEqualTo(2);
        verify(predictionRepository, times(1)).findStatsRowsByUserIdOrderByCreatedAtDesc(7L);
        verify(gameRepository, times(1)).findPredictionStatsGameSummaries(eq(List.of("202603200001", "202603210001")), anyList());
        verify(predictionRepository, never()).findAllByUserIdOrderByCreatedAtDesc(any());
        verify(gameRepository, never()).findByGameIdIn(any());
    }

    // ========== helpers ==========

    /** Creates a mock GameEntity that passes isCanonicalGame() and validateVoteOpen() checks. */
    private GameEntity buildCanonicalMockGame(String gameId) {
        GameEntity game = mock(GameEntity.class);
        lenient().when(game.getGameId()).thenReturn(gameId);
        lenient().when(game.getHomeTeam()).thenReturn("LG");
        lenient().when(game.getAwayTeam()).thenReturn("HH");
        // status=null → not in BLOCKED_VOTE_STATUSES, startTime=null → no time check
        return game;
    }

    private void stubVoteWindowOpen(GameEntity game, String gameId) {
        GameMetadataEntity metadata = mock(GameMetadataEntity.class);
        when(game.getGameDate()).thenReturn(LocalDate.now().plusDays(1));
        when(metadata.getStartTime()).thenReturn(LocalTime.of(18, 30));
        when(gameMetadataRepository.findByGameId(gameId)).thenReturn(Optional.of(metadata));
    }

    private MatchRangeProjection buildRangeMatch(
            String gameId,
            LocalDate gameDate,
            String homeTeam,
            String awayTeam,
            Integer seasonId,
            Integer rawLeagueTypeCode,
            Integer seriesGameNo) {
        MatchRangeProjection match = mock(MatchRangeProjection.class);
        lenient().when(match.getGameId()).thenReturn(gameId);
        lenient().when(match.getGameDate()).thenReturn(gameDate);
        lenient().when(match.getHomeTeam()).thenReturn(homeTeam);
        lenient().when(match.getAwayTeam()).thenReturn(awayTeam);
        lenient().when(match.getStadium()).thenReturn("잠실");
        lenient().when(match.getHomeScore()).thenReturn(0);
        lenient().when(match.getAwayScore()).thenReturn(0);
        lenient().when(match.getIsDummy()).thenReturn(false);
        lenient().when(match.getHomePitcher()).thenReturn(null);
        lenient().when(match.getAwayPitcher()).thenReturn(null);
        lenient().when(match.getSeasonId()).thenReturn(seasonId);
        lenient().when(match.getRawLeagueTypeCode()).thenReturn(rawLeagueTypeCode);
        lenient().when(match.getSeriesGameNo()).thenReturn(seriesGameNo);
        lenient().when(match.getGameStatus()).thenReturn("COMPLETED");
        lenient().when(match.getStartTime()).thenReturn(LocalTime.of(18, 30));
        return match;
    }

    private GameEntity buildGame(String gameId, LocalDate gameDate, String homeTeam, String awayTeam, boolean isDummy) {
        return GameEntity.builder()
                .gameId(gameId)
                .gameDate(gameDate)
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .stadium("잠실")
                .isDummy(isDummy)
                .homeScore(0)
                .awayScore(0)
                .build();
    }

    private GameDetailHeaderProjection buildGameDetailHeader(String gameId, LocalDate gameDate) {
        GameDetailHeaderProjection header = mock(GameDetailHeaderProjection.class);
        lenient().when(header.getGameId()).thenReturn(gameId);
        lenient().when(header.getGameDate()).thenReturn(gameDate);
        lenient().when(header.getStadium()).thenReturn("잠실");
        lenient().when(header.getStadiumName()).thenReturn("잠실야구장");
        lenient().when(header.getStartTime()).thenReturn(LocalTime.of(18, 30));
        lenient().when(header.getAttendance()).thenReturn(12345);
        lenient().when(header.getWeather()).thenReturn("맑음");
        lenient().when(header.getGameTimeMinutes()).thenReturn(185);
        lenient().when(header.getHomeTeam()).thenReturn("LG");
        lenient().when(header.getAwayTeam()).thenReturn("HH");
        lenient().when(header.getHomeScore()).thenReturn(4);
        lenient().when(header.getAwayScore()).thenReturn(2);
        lenient().when(header.getHomePitcher()).thenReturn("임찬규");
        lenient().when(header.getAwayPitcher()).thenReturn("류현진");
        lenient().when(header.getGameStatus()).thenReturn("COMPLETED");
        return header;
    }

    private GameDetailHeaderProjection buildGameDetailHeaderWithoutMetadata(String gameId, LocalDate gameDate) {
        GameDetailHeaderProjection header = buildGameDetailHeader(gameId, gameDate);
        lenient().when(header.getStadiumName()).thenReturn(null);
        lenient().when(header.getStartTime()).thenReturn(null);
        lenient().when(header.getAttendance()).thenReturn(null);
        lenient().when(header.getWeather()).thenReturn(null);
        lenient().when(header.getGameTimeMinutes()).thenReturn(null);
        return header;
    }

    // ──────────────────────────────────────────────────
    // upsertInningScores
    // ──────────────────────────────────────────────────

    @Test
    @DisplayName("upsertInningScores - 정상 저장")
    void upsertInningScoresShouldSaveAll() {
        String gameId = "GAME001";
        GameEntity game = buildGame(gameId, LocalDate.of(2025, 5, 1), "LG", "KT", false);
        when(gameRepository.findByGameId(gameId)).thenReturn(Optional.of(game));
        when(gameInningScoreRepository.deleteAllByGameId(gameId)).thenReturn(0);
        when(gameInningScoreRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        GameInningScoreRequestDto away1 = new GameInningScoreRequestDto();
        setField(away1, "inning", 1);
        setField(away1, "teamSide", "away");
        setField(away1, "teamCode", "KT");
        setField(away1, "runs", 2);
        setField(away1, "isExtra", false);

        GameInningScoreRequestDto home1 = new GameInningScoreRequestDto();
        setField(home1, "inning", 1);
        setField(home1, "teamSide", "home");
        setField(home1, "teamCode", "LG");
        setField(home1, "runs", 0);
        setField(home1, "isExtra", false);

        int saved = predictionService.upsertInningScores(gameId, List.of(away1, home1));

        assertThat(saved).isEqualTo(2);
        assertThat(game.getHomeScore()).isEqualTo(0);
        assertThat(game.getAwayScore()).isEqualTo(2);
        assertThat(game.getGameStatus()).isEqualTo("COMPLETED");
        assertThat(game.getWinningTeam()).isEqualTo("KT");
        assertThat(game.getWinningScore()).isEqualTo(2);
        verify(gameInningScoreRepository).deleteAllByGameId(gameId);
        verify(gameInningScoreRepository).saveAll(anyList());
        verify(gameRepository).saveAndFlush(game);
    }

    @Test
    @DisplayName("upsertInningScores - 10회 이상 row는 isExtra를 true로 정규화한다")
    void upsertInningScoresShouldNormalizeFalseExtraFlags() {
        String gameId = "GAME001_EXTRA_FLAG";
        GameEntity game = buildGame(gameId, LocalDate.of(2025, 5, 1), "LG", "KT", false);
        when(gameRepository.findByGameId(gameId)).thenReturn(Optional.of(game));
        when(gameInningScoreRepository.deleteAllByGameId(gameId)).thenReturn(0);
        when(gameInningScoreRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        GameInningScoreRequestDto away10 = new GameInningScoreRequestDto();
        setField(away10, "inning", 10);
        setField(away10, "teamSide", "away");
        setField(away10, "teamCode", "KT");
        setField(away10, "runs", 1);
        setField(away10, "isExtra", false);

        GameInningScoreRequestDto home10 = new GameInningScoreRequestDto();
        setField(home10, "inning", 10);
        setField(home10, "teamSide", "home");
        setField(home10, "teamCode", "LG");
        setField(home10, "runs", 0);
        setField(home10, "isExtra", false);

        predictionService.upsertInningScores(gameId, List.of(away10, home10));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GameInningScoreEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(gameInningScoreRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(GameInningScoreEntity::getIsExtra)
                .containsExactly(true, true);
    }

    @Test
    @DisplayName("upsertInningScores - 당일 시작된 경기는 LIVE로 동기화")
    void upsertInningScoresShouldSyncLiveStatusForStartedTodayGame() {
        String gameId = "GAME002";
        GameEntity game = buildGame(gameId, LocalDate.now(), "LG", "KT", false);
        when(gameRepository.findByGameId(gameId)).thenReturn(Optional.of(game));
        when(gameInningScoreRepository.deleteAllByGameId(gameId)).thenReturn(0);
        when(gameInningScoreRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        GameMetadataEntity metadata = mock(GameMetadataEntity.class);
        when(metadata.getStartTime()).thenReturn(LocalTime.now().minusHours(1));
        when(gameMetadataRepository.findByGameId(gameId)).thenReturn(Optional.of(metadata));

        GameInningScoreRequestDto away1 = new GameInningScoreRequestDto();
        setField(away1, "inning", 1);
        setField(away1, "teamSide", "away");
        setField(away1, "teamCode", "KT");
        setField(away1, "runs", 1);
        setField(away1, "isExtra", false);

        predictionService.upsertInningScores(gameId, List.of(away1));

        assertThat(game.getHomeScore()).isEqualTo(0);
        assertThat(game.getAwayScore()).isEqualTo(1);
        assertThat(game.getGameStatus()).isEqualTo("LIVE");
        assertThat(game.getWinningTeam()).isNull();
        assertThat(game.getWinningScore()).isNull();
    }

    @Test
    @DisplayName("upsertInningScores - 득점 정보가 없는 placeholder row는 거부한다")
    void upsertInningScoresShouldRejectPlaceholderRows() {
        String gameId = "GAME002_PLACEHOLDER";
        GameEntity game = buildGame(gameId, LocalDate.of(2025, 5, 1), "LG", "KT", false);
        when(gameRepository.findByGameId(gameId)).thenReturn(Optional.of(game));

        GameInningScoreRequestDto placeholder = new GameInningScoreRequestDto();
        setField(placeholder, "inning", 10);
        setField(placeholder, "teamSide", "away");
        setField(placeholder, "teamCode", "KT");

        assertThrows(IllegalArgumentException.class,
                () -> predictionService.upsertInningScores(gameId, List.of(placeholder)));

        verify(gameInningScoreRepository, never()).deleteAllByGameId(gameId);
        verify(gameInningScoreRepository, never()).saveAll(anyList());
        verify(gameRepository, never()).saveAndFlush(game);
    }

    @Test
    @DisplayName("syncGameSnapshot - 저장된 이닝 스코어 기준으로 경기 상태를 복구한다")
    void syncGameSnapshotShouldRepairFromStoredInningScores() {
        String gameId = "GAME003";
        GameEntity game = buildGame(gameId, LocalDate.of(2025, 5, 1), "LG", "KT", false);
        game.setGameStatus("SCHEDULED");
        when(gameRepository.findByGameId(gameId)).thenReturn(Optional.of(game));
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc(gameId)).thenReturn(List.of(
                GameInningScoreEntity.builder().gameId(gameId).inning(1).teamSide("away").runs(2).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(3).teamSide("away").runs(1).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(5).teamSide("home").runs(4).build()
        ));

        GameScoreSyncResultDto result = predictionService.syncGameSnapshot(gameId);

        assertThat(result.synced()).isTrue();
        assertThat(result.usedInningScores()).isTrue();
        assertThat(result.inningScoreCount()).isEqualTo(3);
        assertThat(result.homeScore()).isEqualTo(4);
        assertThat(result.awayScore()).isEqualTo(3);
        assertThat(result.gameStatus()).isEqualTo("COMPLETED");
        assertThat(result.winningTeam()).isEqualTo("LG");
        assertThat(result.winningScore()).isEqualTo(4);
        verify(gameRepository).saveAndFlush(game);
    }

    @Test
    @DisplayName("syncGameSnapshot - 이닝 데이터가 없어도 기존 총점으로 상태를 복구한다")
    void syncGameSnapshotShouldFallbackToExistingScores() {
        String gameId = "GAME004";
        GameEntity game = buildGame(gameId, LocalDate.of(2025, 5, 1), "LG", "KT", false);
        game.setHomeScore(1);
        game.setAwayScore(1);
        game.setGameStatus("SCHEDULED");
        when(gameRepository.findByGameId(gameId)).thenReturn(Optional.of(game));
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc(gameId)).thenReturn(List.of());

        GameScoreSyncResultDto result = predictionService.syncGameSnapshot(gameId);

        assertThat(result.synced()).isTrue();
        assertThat(result.usedInningScores()).isFalse();
        assertThat(result.gameStatus()).isEqualTo("DRAW");
        assertThat(result.winningTeam()).isNull();
        assertThat(result.winningScore()).isNull();
        verify(gameRepository).saveAndFlush(game);
    }

    @Test
    @DisplayName("syncGameSnapshot - placeholder row는 합산 대상에서 제외한다")
    void syncGameSnapshotShouldIgnorePlaceholderRows() {
        String gameId = "GAME004_PLACEHOLDER";
        GameEntity game = buildGame(gameId, LocalDate.of(2025, 5, 1), "LG", "KT", false);
        game.setHomeScore(1);
        game.setAwayScore(1);
        game.setGameStatus("SCHEDULED");
        when(gameRepository.findByGameId(gameId)).thenReturn(Optional.of(game));
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc(gameId)).thenReturn(List.of(
                GameInningScoreEntity.builder().gameId(gameId).inning(10).teamSide("away").runs(null).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(10).teamSide("home").runs(null).build()
        ));

        GameScoreSyncResultDto result = predictionService.syncGameSnapshot(gameId);

        assertThat(result.synced()).isTrue();
        assertThat(result.usedInningScores()).isFalse();
        assertThat(result.inningScoreCount()).isEqualTo(0);
        assertThat(result.homeScore()).isEqualTo(1);
        assertThat(result.awayScore()).isEqualTo(1);
        assertThat(result.gameStatus()).isEqualTo("DRAW");
        verify(gameRepository).saveAndFlush(game);
    }

    @Test
    @DisplayName("syncGameSnapshot - 점수 미집계 경기의 0점 template row는 합산 대상에서 제외한다")
    void syncGameSnapshotShouldIgnoreZeroTemplateRowsWithoutKnownScore() {
        String gameId = "GAME004_ZERO_TEMPLATE";
        GameEntity game = buildGame(gameId, LocalDate.of(2025, 5, 1), "LG", "KT", false);
        game.setHomeScore(null);
        game.setAwayScore(null);
        game.setGameStatus("SCHEDULED");
        when(gameRepository.findByGameId(gameId)).thenReturn(Optional.of(game));
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc(gameId)).thenReturn(List.of(
                GameInningScoreEntity.builder().gameId(gameId).inning(1).teamSide("away").runs(0).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(1).teamSide("home").runs(0).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(12).teamSide("away").runs(0).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(12).teamSide("home").runs(0).build()
        ));

        GameScoreSyncResultDto result = predictionService.syncGameSnapshot(gameId);

        assertThat(result.synced()).isFalse();
        assertThat(result.usedInningScores()).isFalse();
        assertThat(result.inningScoreCount()).isEqualTo(0);
        assertThat(result.gameStatus()).isEqualTo("SCHEDULED");
        verify(gameRepository, never()).saveAndFlush(game);
    }

    @Test
    @DisplayName("syncGameSnapshot - 최종 점수가 없어도 승패 확정 뒤의 0점 extra inning template은 제외한다")
    void syncGameSnapshotShouldTrimZeroTemplateRowsAfterDecisiveInningWithoutKnownScore() {
        String gameId = "GAME004_ZERO_TEMPLATE_DECISIVE";
        GameEntity game = buildGame(gameId, LocalDate.of(2025, 5, 1), "SSG", "KIA", false);
        game.setHomeScore(null);
        game.setAwayScore(null);
        game.setGameStatus("SCHEDULED");
        when(gameRepository.findByGameId(gameId)).thenReturn(Optional.of(game));
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc(gameId)).thenReturn(List.of(
                GameInningScoreEntity.builder().gameId(gameId).inning(1).teamSide("away").runs(0).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(1).teamSide("home").runs(0).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(2).teamSide("home").runs(4).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(3).teamSide("home").runs(5).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(4).teamSide("away").runs(2).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(4).teamSide("home").runs(1).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(7).teamSide("away").runs(4).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(8).teamSide("home").runs(1).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(9).teamSide("away").runs(0).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(9).teamSide("home").runs(0).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(10).teamSide("away").runs(0).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(10).teamSide("home").runs(0).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(11).teamSide("away").runs(0).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(11).teamSide("home").runs(0).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(12).teamSide("away").runs(0).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(12).teamSide("home").runs(0).build()
        ));

        GameScoreSyncResultDto result = predictionService.syncGameSnapshot(gameId);

        assertThat(result.synced()).isTrue();
        assertThat(result.usedInningScores()).isTrue();
        assertThat(result.inningScoreCount()).isEqualTo(10);
        assertThat(result.homeScore()).isEqualTo(11);
        assertThat(result.awayScore()).isEqualTo(6);
        assertThat(result.gameStatus()).isEqualTo("COMPLETED");
        assertThat(result.winningTeam()).isEqualTo("SSG");
        verify(gameRepository).saveAndFlush(game);
    }

    @Test
    @DisplayName("syncGameSnapshotsByDateRange - 날짜 범위의 경기들을 일괄 동기화한다")
    void syncGameSnapshotsByDateRangeShouldSyncGames() {
        LocalDate startDate = LocalDate.of(2025, 5, 1);
        LocalDate endDate = LocalDate.of(2025, 5, 2);
        GameEntity first = buildGame("GAME101", LocalDate.of(2025, 5, 1), "LG", "KT", false);
        first.setGameStatus("SCHEDULED");
        GameEntity second = buildGame("GAME102", LocalDate.of(2025, 5, 2), "SSG", "KIA", false);
        second.setGameStatus("SCHEDULED");

        when(gameRepository.findAllByDateRange(startDate, endDate)).thenReturn(List.of(first, second));
        when(gameRepository.findByGameId("GAME101")).thenReturn(Optional.of(first));
        when(gameRepository.findByGameId("GAME102")).thenReturn(Optional.of(second));
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc("GAME101")).thenReturn(List.of(
                GameInningScoreEntity.builder().gameId("GAME101").inning(1).teamSide("home").runs(2).build(),
                GameInningScoreEntity.builder().gameId("GAME101").inning(2).teamSide("away").runs(1).build()
        ));
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc("GAME102")).thenReturn(List.of());
        second.setHomeScore(3);
        second.setAwayScore(3);

        GameScoreSyncBatchResultDto result = predictionService.syncGameSnapshotsByDateRange(startDate, endDate);

        assertThat(result.totalGames()).isEqualTo(2);
        assertThat(result.syncedGames()).isEqualTo(2);
        assertThat(result.skippedGames()).isEqualTo(0);
        assertThat(result.results()).hasSize(2);
        assertThat(first.getGameStatus()).isEqualTo("COMPLETED");
        assertThat(second.getGameStatus()).isEqualTo("DRAW");
    }

    @Test
    @DisplayName("syncGameSnapshotsByDateRange - 31일 초과 범위는 예외 발생")
    void syncGameSnapshotsByDateRangeShouldRejectTooWideRange() {
        assertThrows(IllegalArgumentException.class,
                () -> predictionService.syncGameSnapshotsByDateRange(
                        LocalDate.of(2025, 5, 1),
                        LocalDate.of(2025, 6, 1)
                ));
    }

    @Test
    @DisplayName("findGameStatusMismatchesByDateRange - stale status 경기만 진단 결과에 포함한다")
    void findGameStatusMismatchesByDateRangeShouldReturnOnlyMismatches() {
        LocalDate date = LocalDate.of(2025, 5, 1);
        GameEntity stale = buildGame("GAME201", date, "LG", "KT", false);
        stale.setGameStatus("SCHEDULED");
        stale.setHomeScore(4);
        stale.setAwayScore(2);
        GameEntity alreadyNormalized = buildGame("GAME202", date, "SSG", "KIA", false);
        alreadyNormalized.setGameStatus("COMPLETED");
        alreadyNormalized.setHomeScore(3);
        alreadyNormalized.setAwayScore(1);

        when(gameRepository.findAllByDateRange(date, date)).thenReturn(List.of(stale, alreadyNormalized));
        when(gameMetadataRepository.findByGameId("GAME201")).thenReturn(Optional.empty());
        when(gameMetadataRepository.findByGameId("GAME202")).thenReturn(Optional.empty());
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc("GAME201")).thenReturn(List.of());
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc("GAME202")).thenReturn(List.of());

        GameStatusMismatchBatchResultDto result = predictionService.findGameStatusMismatchesByDateRange(date, date);

        assertThat(result.totalGames()).isEqualTo(2);
        assertThat(result.mismatchCount()).isEqualTo(1);
        assertThat(result.mismatches()).hasSize(1);
        assertThat(result.mismatches().get(0).gameId()).isEqualTo("GAME201");
        assertThat(result.mismatches().get(0).normalizedRawStatus()).isEqualTo("SCHEDULED");
        assertThat(result.mismatches().get(0).effectiveStatus()).isEqualTo("COMPLETED");
        assertThat(result.mismatches().get(0).reasons()).contains("score_present");
    }

    @Test
    @DisplayName("findGameStatusMismatchesByDateRange - placeholder row만 있으면 진행 데이터로 보지 않는다")
    void findGameStatusMismatchesByDateRangeShouldIgnorePlaceholderRows() {
        LocalDate date = LocalDate.now();
        GameEntity game = buildGame("GAME202_PLACEHOLDER", date, "LG", "KT", false);
        game.setGameStatus("SCHEDULED");
        game.setHomeScore(null);
        game.setAwayScore(null);

        when(gameRepository.findAllByDateRange(date, date)).thenReturn(List.of(game));
        when(gameMetadataRepository.findByGameId("GAME202_PLACEHOLDER"))
                .thenReturn(Optional.of(GameMetadataEntity.builder()
                        .gameId("GAME202_PLACEHOLDER")
                        .startTime(LocalTime.MIDNIGHT)
                        .build()));
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc("GAME202_PLACEHOLDER"))
                .thenReturn(List.of(
                        GameInningScoreEntity.builder().gameId("GAME202_PLACEHOLDER").inning(10).teamSide("away").runs(null).build(),
                        GameInningScoreEntity.builder().gameId("GAME202_PLACEHOLDER").inning(10).teamSide("home").runs(null).build()
                ));

        GameStatusMismatchBatchResultDto result = predictionService.findGameStatusMismatchesByDateRange(date, date);

        assertThat(result.mismatchCount()).isEqualTo(0);
        assertThat(result.mismatches()).isEmpty();
    }

    @Test
    @DisplayName("findGameStatusMismatchesByDateRange - 점수 미집계 경기의 0점 template row는 mismatch로 보지 않는다")
    void findGameStatusMismatchesByDateRangeShouldIgnoreZeroTemplateRows() {
        LocalDate date = LocalDate.now();
        GameEntity game = buildGame("GAME202_ZERO_TEMPLATE", date, "LG", "KT", false);
        game.setGameStatus("SCHEDULED");
        game.setHomeScore(null);
        game.setAwayScore(null);

        when(gameRepository.findAllByDateRange(date, date)).thenReturn(List.of(game));
        when(gameMetadataRepository.findByGameId("GAME202_ZERO_TEMPLATE"))
                .thenReturn(Optional.of(GameMetadataEntity.builder()
                        .gameId("GAME202_ZERO_TEMPLATE")
                        .startTime(LocalTime.MIDNIGHT)
                        .build()));
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc("GAME202_ZERO_TEMPLATE"))
                .thenReturn(List.of(
                        GameInningScoreEntity.builder().gameId("GAME202_ZERO_TEMPLATE").inning(1).teamSide("away").runs(0).build(),
                        GameInningScoreEntity.builder().gameId("GAME202_ZERO_TEMPLATE").inning(1).teamSide("home").runs(0).build(),
                        GameInningScoreEntity.builder().gameId("GAME202_ZERO_TEMPLATE").inning(12).teamSide("away").runs(0).build(),
                        GameInningScoreEntity.builder().gameId("GAME202_ZERO_TEMPLATE").inning(12).teamSide("home").runs(0).build()
                ));

        GameStatusMismatchBatchResultDto result = predictionService.findGameStatusMismatchesByDateRange(date, date);

        assertThat(result.mismatchCount()).isEqualTo(0);
        assertThat(result.mismatches()).isEmpty();
    }

    @Test
    @DisplayName("findGameStatusMismatchesByDateRange - 비정상 팀 코드 raw row를 별도 목록으로 반환한다")
    void findGameStatusMismatchesByDateRangeShouldReturnNonCanonicalGames() {
        LocalDate date = LocalDate.of(2026, 4, 14);
        GameEntity malformed = buildGame("GAME204", date, "0LG", "롯데0", false);
        malformed.setGameStatus("SCHEDULED");
        malformed.setHomeScore(null);
        malformed.setAwayScore(null);

        when(gameRepository.findAllByDateRange(date, date)).thenReturn(List.of(malformed));
        when(gameMetadataRepository.findByGameId("GAME204"))
                .thenReturn(Optional.of(GameMetadataEntity.builder()
                        .gameId("GAME204")
                        .startTime(LocalTime.of(18, 30))
                        .build()));

        GameStatusMismatchBatchResultDto result = predictionService.findGameStatusMismatchesByDateRange(date, date);

        assertThat(result.totalGames()).isEqualTo(1);
        assertThat(result.mismatchCount()).isEqualTo(0);
        assertThat(result.nonCanonicalCount()).isEqualTo(1);
        assertThat(result.nonCanonicalGames()).hasSize(1);
        assertThat(result.nonCanonicalGames().get(0).gameId()).isEqualTo("GAME204");
        assertThat(result.nonCanonicalGames().get(0).startTime()).isEqualTo(LocalTime.of(18, 30));
        assertThat(result.nonCanonicalGames().get(0).homeTeam()).isEqualTo("0LG");
        assertThat(result.nonCanonicalGames().get(0).awayTeam()).isEqualTo("롯데0");
        assertThat(result.nonCanonicalGames().get(0).reasons())
                .containsExactlyInAnyOrder("non_canonical_home_team", "non_canonical_away_team");
    }

    @Test
    @DisplayName("repairGameStatusMismatchesByDateRange - dryRun이면 실제 복구 없이 대상만 반환한다")
    void repairGameStatusMismatchesByDateRangeShouldSupportDryRun() {
        LocalDate date = LocalDate.of(2025, 5, 1);
        GameEntity stale = buildGame("GAME301", date, "LG", "KT", false);
        stale.setGameStatus("SCHEDULED");
        stale.setHomeScore(5);
        stale.setAwayScore(3);

        when(gameRepository.findAllByDateRange(date, date)).thenReturn(List.of(stale));
        when(gameMetadataRepository.findByGameId("GAME301")).thenReturn(Optional.empty());
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc("GAME301")).thenReturn(List.of());

        GameStatusRepairBatchResultDto result = predictionService
                .repairGameStatusMismatchesByDateRange(date, date, true);

        assertThat(result.dryRun()).isTrue();
        assertThat(result.mismatchCount()).isEqualTo(1);
        assertThat(result.repairedCount()).isEqualTo(0);
        assertThat(result.repairedGames()).isEmpty();
        assertThat(stale.getGameStatus()).isEqualTo("SCHEDULED");
    }

    @Test
    @DisplayName("repairGameStatusMismatchesByDateRange - apply면 mismatch 경기만 복구한다")
    void repairGameStatusMismatchesByDateRangeShouldRepairMismatches() {
        LocalDate date = LocalDate.of(2025, 5, 1);
        GameEntity stale = buildGame("GAME302", date, "LG", "KT", false);
        stale.setGameStatus("SCHEDULED");
        stale.setHomeScore(2);
        stale.setAwayScore(1);
        GameEntity healthy = buildGame("GAME303", date, "SSG", "KIA", false);
        healthy.setGameStatus("COMPLETED");
        healthy.setHomeScore(4);
        healthy.setAwayScore(3);

        when(gameRepository.findAllByDateRange(date, date)).thenReturn(List.of(stale, healthy));
        when(gameMetadataRepository.findByGameId("GAME302")).thenReturn(Optional.empty());
        when(gameMetadataRepository.findByGameId("GAME303")).thenReturn(Optional.empty());
        when(gameRepository.findByGameId("GAME302")).thenReturn(Optional.of(stale));
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc("GAME302")).thenReturn(List.of());
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc("GAME303")).thenReturn(List.of());

        GameStatusRepairBatchResultDto result = predictionService
                .repairGameStatusMismatchesByDateRange(date, date, false);

        assertThat(result.dryRun()).isFalse();
        assertThat(result.mismatchCount()).isEqualTo(1);
        assertThat(result.repairedCount()).isEqualTo(1);
        assertThat(result.repairedGames()).hasSize(1);
        assertThat(result.repairedGames().get(0).gameId()).isEqualTo("GAME302");
        assertThat(stale.getGameStatus()).isEqualTo("COMPLETED");
        assertThat(healthy.getGameStatus()).isEqualTo("COMPLETED");
        verify(gameRepository).saveAndFlush(stale);
    }

    @Test
    @DisplayName("repairGameStatusMismatchesByDateRange - 비정상 팀 코드 raw row는 복구하지 않고 그대로 반환한다")
    void repairGameStatusMismatchesByDateRangeShouldKeepNonCanonicalGames() {
        LocalDate date = LocalDate.of(2026, 4, 14);
        GameEntity stale = buildGame("GAME304", date, "LG", "KT", false);
        stale.setGameStatus("SCHEDULED");
        stale.setHomeScore(4);
        stale.setAwayScore(2);
        GameEntity malformed = buildGame("GAME305", date, "0LG", "롯데0", false);
        malformed.setGameStatus("SCHEDULED");
        malformed.setHomeScore(null);
        malformed.setAwayScore(null);

        when(gameRepository.findAllByDateRange(date, date)).thenReturn(List.of(stale, malformed));
        when(gameRepository.findByGameId("GAME304")).thenReturn(Optional.of(stale));
        when(gameMetadataRepository.findByGameId("GAME304")).thenReturn(Optional.empty());
        when(gameMetadataRepository.findByGameId("GAME305")).thenReturn(Optional.empty());
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc("GAME304")).thenReturn(List.of());

        GameStatusRepairBatchResultDto result = predictionService
                .repairGameStatusMismatchesByDateRange(date, date, false);

        assertThat(result.mismatchCount()).isEqualTo(1);
        assertThat(result.repairedCount()).isEqualTo(1);
        assertThat(result.nonCanonicalCount()).isEqualTo(1);
        assertThat(result.nonCanonicalGames()).hasSize(1);
        assertThat(result.nonCanonicalGames().get(0).gameId()).isEqualTo("GAME305");
        verify(gameRepository).saveAndFlush(stale);
    }

    @Test
    @DisplayName("upsertInningScores - 존재하지 않는 gameId는 예외 발생")
    void upsertInningScoresShouldThrowWhenGameNotFound() {
        String gameId = "NOTEXIST";
        when(gameRepository.findByGameId(gameId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> predictionService.upsertInningScores(gameId, List.of()));
    }

    /** NoArgsConstructor DTO의 private 필드를 reflection으로 설정 */
    private static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
