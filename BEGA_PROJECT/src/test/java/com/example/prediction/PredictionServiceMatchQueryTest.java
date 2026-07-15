package com.example.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.example.kbo.repository.CanonicalAdjacentGameDatesProjection;
import com.example.kbo.repository.CanonicalGameDateBoundsProjection;
import com.example.kbo.repository.MatchRangeProjection;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PredictionServiceMatchQueryTest extends PredictionServiceTestFixture {

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
    void getMatchesByDateRangeShouldCacheSuccessfulRangeForSameRequest() {
        LocalDate startDate = LocalDate.now().plusDays(1);
        LocalDate endDate = startDate.plusDays(6);
        MatchRangeProjection canonical = buildRangeMatch("20260618HHNC0", startDate, "HH", "NC", 20260, 0, null);

        when(gameRepository.findCanonicalRangeProjectionByDateRangeNoCount(
                eq(startDate),
                eq(endDate),
                anyList(),
                any(Pageable.class)
        )).thenReturn(List.of(canonical));

        List<MatchDto> first = predictionService.getMatchesByDateRange(startDate, endDate, false, 0, 20);
        List<MatchDto> second = predictionService.getMatchesByDateRange(startDate, endDate, false, 0, 20);

        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
        assertThat(second.get(0).getGameId()).isEqualTo("20260618HHNC0");
        verify(gameRepository, times(1)).findCanonicalRangeProjectionByDateRangeNoCount(
                eq(startDate),
                eq(endDate),
                anyList(),
                any(Pageable.class));
        verify(baseballDataIntegrityGuard, times(1))
                .ensurePredictionRangeMatches(eq("prediction.matches_by_range"), anyList());
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
        // seriesGameNo is now computed in Java from the result set (DB correlated subquery removed).
        // Single game in the result → relative position is 1.
        assertThat(match.getSeriesGameNo()).isEqualTo(1);
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
        // seriesGameNo is now computed in Java from the result set (DB correlated subquery removed).
        // Single game in the result → relative position is 1.
        assertThat(match.getSeriesGameNo()).isEqualTo(1);
    }

    @Test
    @DisplayName("getMatchesByDateRange computes seriesGameNo in Java from result set")
    void getMatchesByDateRange_computesSeriesGameNoFromResultSet() {
        LocalDate gameDate = LocalDate.of(2025, 10, 18);
        // Simulate a paged result that contains only one game (page starts mid-series).
        // With Java-level computation the relative position within the returned page is 1.
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

        // Single game in result set → Java computation assigns seriesGameNo=1
        assertThat(matches).extracting(MatchDto::getSeriesGameNo).containsExactly(1);
    }

    @Test
    @DisplayName("getMatchesByDateRange assigns sequential seriesGameNo for multi-game postseason series")
    void getMatchesByDateRange_assignsSequentialSeriesGameNoForMultiplePostseasonGames() {
        LocalDate day1 = LocalDate.of(2025, 10, 18);
        LocalDate day2 = LocalDate.of(2025, 10, 19);
        LocalDate day3 = LocalDate.of(2025, 10, 20);
        // Three games of the same postseason series (HH vs LG, PO), sorted ascending
        MatchRangeProjection game1 = buildRangeMatch("202510180001", day1, "HH", "LG", 264, 4, 1);
        MatchRangeProjection game2 = buildRangeMatch("202510190001", day2, "HH", "LG", 264, 4, 2);
        MatchRangeProjection game3 = buildRangeMatch("202510200001", day3, "HH", "LG", 264, 4, 3);

        when(gameRepository.findCanonicalRangeProjectionByDateRangeNoCount(
                any(LocalDate.class),
                any(LocalDate.class),
                anyList(),
                any(Pageable.class)
        )).thenReturn(List.of(game1, game2, game3));
        when(gameRepository.findConfiguredStartDateByTypeFromSeasonYear(anyInt(), anyInt()))
                .thenReturn(Optional.empty());

        List<MatchDto> matches = predictionService.getMatchesByDateRange(day1, day3);

        assertThat(matches).extracting(MatchDto::getSeriesGameNo).containsExactly(1, 2, 3);
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
    void getMatchesByDateShouldReturnEmptyForCanonicalOffDay() {
        LocalDate targetDate = LocalDate.of(2026, 4, 27);
        CanonicalAdjacentGameDatesProjection adjacentDates = mock(CanonicalAdjacentGameDatesProjection.class);

        when(gameRepository.findCanonicalRangeProjectionByGameDate(
                org.mockito.ArgumentMatchers.eq(targetDate),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of());
        when(adjacentDates.getPrevDate()).thenReturn(LocalDate.of(2026, 4, 26));
        when(adjacentDates.getNextDate()).thenReturn(LocalDate.of(2026, 4, 28));
        when(gameRepository.findCanonicalAdjacentGameDates(
                org.mockito.ArgumentMatchers.eq(targetDate),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(adjacentDates);

        List<MatchDto> matches = predictionService.getMatchesByDate(targetDate);

        assertThat(matches).isEmpty();
        verify(baseballDataIntegrityGuard, never()).ensurePredictionDateMatches(
                eq("prediction.matches_by_date"),
                eq(targetDate),
                anyList());
    }

    @Test
    void getMatchDayNavigationShouldReturnEmptyGamesForCanonicalOffDay() {
        LocalDate targetDate = LocalDate.of(2026, 4, 27);
        LocalDate prevDate = LocalDate.of(2026, 4, 26);
        LocalDate nextDate = LocalDate.of(2026, 4, 28);
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
        verify(baseballDataIntegrityGuard, never()).ensurePredictionDateMatches(
                eq("prediction.matches_by_date"),
                eq(targetDate),
                anyList());
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
    void getMatchDayNavigationShouldPopulateWinProbabilityForScheduledGames() {
        LocalDate targetDate = LocalDate.of(2026, 5, 6);
        MatchRangeProjection scheduled = buildRangeMatch("202605060001", targetDate, "LG", "KT", 20260, 0, null);
        when(scheduled.getGameStatus()).thenReturn("SCHEDULED");
        when(scheduled.getHomeScore()).thenReturn(null);
        when(scheduled.getAwayScore()).thenReturn(null);
        when(scheduled.getHomePitcher()).thenReturn("임찬규");
        when(scheduled.getAwayPitcher()).thenReturn(null);
        when(gameRepository.findCanonicalRangeProjectionByGameDate(
                org.mockito.ArgumentMatchers.eq(targetDate),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(scheduled));
        when(gameRepository.findCanonicalAdjacentGameDates(
                org.mockito.ArgumentMatchers.eq(targetDate),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(null);

        MatchDayNavigationResponseDto response = predictionService.getMatchDayNavigation(targetDate);

        assertThat(response.getGames()).hasSize(1);
        MatchDto match = response.getGames().get(0);
        assertThat(match.getGameStatus()).isEqualTo("SCHEDULED");
        assertThat(match.getWinProbability()).isNotNull();
        assertThat(match.getWinProbability().getHome()).isBetween(35.0, 65.0);
        assertThat(match.getWinProbability().getAway()).isBetween(35.0, 65.0);
        assertThat(match.getWinProbability().getHome() + match.getWinProbability().getAway())
                .isEqualTo(100.0);
    }

    @Test
    void getMatchDayNavigationShouldNotPopulateWinProbabilityForCompletedGames() {
        LocalDate targetDate = LocalDate.of(2026, 5, 7);
        MatchRangeProjection completed = buildRangeMatch("202605070001", targetDate, "LG", "KT", 20260, 0, null);
        when(completed.getHomeScore()).thenReturn(5);
        when(completed.getAwayScore()).thenReturn(3);
        when(gameRepository.findCanonicalRangeProjectionByGameDate(
                org.mockito.ArgumentMatchers.eq(targetDate),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(List.of(completed));
        when(gameRepository.findCanonicalAdjacentGameDates(
                org.mockito.ArgumentMatchers.eq(targetDate),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(null);

        MatchDayNavigationResponseDto response = predictionService.getMatchDayNavigation(targetDate);

        assertThat(response.getGames()).hasSize(1);
        MatchDto match = response.getGames().get(0);
        assertThat(match.getGameStatus()).isEqualTo("COMPLETED");
        assertThat(match.getWinProbability()).isNull();
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
}
