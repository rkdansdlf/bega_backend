package com.example.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.example.kbo.repository.CanonicalAdjacentGameDatesProjection;
import com.example.kbo.repository.MatchRangeProjection;
import com.example.kbo.validation.ManualBaseballDataMissingItem;
import com.example.kbo.validation.ManualBaseballDataRequest;
import com.example.kbo.validation.ManualBaseballDataRequiredException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PredictionServiceManualDataTest extends PredictionServiceTestFixture {

    @Test
    void getMatchesByDateShouldFailFastForConfiguredManualDataDate() {
        LocalDate targetDate = LocalDate.of(2026, 6, 18);
        PredictionService service = predictionServiceWithManualDataDates(targetDate);

        ManualBaseballDataRequiredException exception = assertThrows(
                ManualBaseballDataRequiredException.class,
                () -> service.getMatchesByDate(targetDate));

        assertThat(exception.getData()).isInstanceOf(ManualBaseballDataRequest.class);
        ManualBaseballDataRequest request = (ManualBaseballDataRequest) exception.getData();
        assertThat(request.scope()).isEqualTo("prediction.matches_by_date");
        assertThat(request.missingItems())
                .extracting(ManualBaseballDataMissingItem::key)
                .containsExactly("game_status", "final_score");
        verify(gameRepository, never()).findCanonicalRangeProjectionByGameDate(eq(targetDate), anyList());
        verify(baseballDataIntegrityGuard, never())
                .ensurePredictionDateMatches(eq("prediction.matches_by_date"), eq(targetDate), anyList());
    }

    @Test
    void getMatchDayNavigationShouldFailFastForConfiguredManualDataDate() {
        LocalDate targetDate = LocalDate.of(2026, 6, 18);
        PredictionService service = predictionServiceWithManualDataDates(targetDate);

        ManualBaseballDataRequiredException exception = assertThrows(
                ManualBaseballDataRequiredException.class,
                () -> service.getMatchDayNavigation(targetDate));

        assertThat(exception.getData()).isInstanceOf(ManualBaseballDataRequest.class);
        ManualBaseballDataRequest request = (ManualBaseballDataRequest) exception.getData();
        assertThat(request.scope()).isEqualTo("prediction.matches_by_date");
        verify(gameRepository, never()).findCanonicalRangeProjectionByGameDate(eq(targetDate), anyList());
        verify(gameRepository, never()).findCanonicalAdjacentGameDates(eq(targetDate), anyList());
        verify(baseballDataIntegrityGuard, never())
                .ensurePredictionDateMatches(eq("prediction.matches_by_date"), eq(targetDate), anyList());
    }

    @Test
    void getMatchesByDateRangeWithMetadataShouldFailFastForConfiguredManualDataDateInRange() {
        LocalDate targetDate = LocalDate.of(2026, 6, 18);
        LocalDate endDate = LocalDate.of(2026, 6, 24);
        PredictionService service = predictionServiceWithManualDataDates(targetDate);

        ManualBaseballDataRequiredException exception = assertThrows(
                ManualBaseballDataRequiredException.class,
                () -> service.getMatchesByDateRangeWithMetadata(targetDate, endDate, true, 0, 20));

        assertThat(exception.getData()).isInstanceOf(ManualBaseballDataRequest.class);
        ManualBaseballDataRequest request = (ManualBaseballDataRequest) exception.getData();
        assertThat(request.scope()).isEqualTo("prediction.matches_by_range");
        verify(gameRepository, never()).findCanonicalRangeProjectionByDateRange(
                eq(targetDate),
                eq(endDate),
                anyList(),
                any(Pageable.class));
        verify(baseballDataIntegrityGuard, never())
                .ensurePredictionRangeMatches(eq("prediction.matches_by_range"), anyList());
    }



    @Test
    void getMatchesByDateRangeWithMetadataShouldCacheManualDataRequiredForSameRange() {
        LocalDate startDate = LocalDate.of(2026, 6, 16);
        LocalDate endDate = LocalDate.of(2026, 6, 17);
        MatchRangeProjection incomplete = buildRangeMatch(
                "20260616HHNC0",
                startDate,
                "HH",
                "NC",
                20260,
                0,
                null);
        PageRequest pageable = PageRequest.of(0, 20);
        ManualBaseballDataRequest manualRequest = new ManualBaseballDataRequest(
                "prediction.matches_by_range",
                List.of(new ManualBaseballDataMissingItem(
                        "20260616HHNC0",
                        "20260616HHNC0",
                        "missing game_status, final_score",
                        "operator-provided final KBO game data")),
                "운영자가 경기 결과 데이터를 수동으로 제공해야 합니다.",
                true);

        when(gameRepository.findCanonicalRangeProjectionByDateRange(
                eq(startDate),
                eq(endDate),
                anyList(),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(incomplete), pageable, 1));
        doThrow(new ManualBaseballDataRequiredException(manualRequest))
                .when(baseballDataIntegrityGuard)
                .ensurePredictionRangeMatches(eq("prediction.matches_by_range"), anyList());

        ManualBaseballDataRequiredException first = assertThrows(
                ManualBaseballDataRequiredException.class,
                () -> predictionService.getMatchesByDateRangeWithMetadata(startDate, endDate, true, 0, 20));
        ManualBaseballDataRequiredException second = assertThrows(
                ManualBaseballDataRequiredException.class,
                () -> predictionService.getMatchesByDateRangeWithMetadata(startDate, endDate, true, 0, 20));

        assertThat(first.getData()).isEqualTo(manualRequest);
        assertThat(second.getData()).isEqualTo(manualRequest);
        verify(gameRepository, times(1)).findCanonicalRangeProjectionByDateRange(
                eq(startDate),
                eq(endDate),
                anyList(),
                any(Pageable.class));
        verify(baseballDataIntegrityGuard, times(1))
                .ensurePredictionRangeMatches(eq("prediction.matches_by_range"), anyList());
    }

    @Test
    void getMatchesByDateShouldCacheManualDataRequiredForSameDate() {
        LocalDate targetDate = LocalDate.of(2026, 6, 17);
        MatchRangeProjection incomplete = buildRangeMatch(
                "20260617HHNC0",
                targetDate,
                "HH",
                "NC",
                20260,
                0,
                null);
        ManualBaseballDataRequest manualRequest = new ManualBaseballDataRequest(
                "prediction.matches_by_date",
                List.of(new ManualBaseballDataMissingItem(
                        "20260617HHNC0",
                        "20260617HHNC0",
                        "missing game_status, final_score",
                        "operator-provided final KBO game data")),
                "운영자가 경기 결과 데이터를 수동으로 제공해야 합니다.",
                true);

        when(gameRepository.findCanonicalRangeProjectionByGameDate(eq(targetDate), anyList()))
                .thenReturn(List.of(incomplete));
        doThrow(new ManualBaseballDataRequiredException(manualRequest))
                .when(baseballDataIntegrityGuard)
                .ensurePredictionDateMatches(eq("prediction.matches_by_date"), eq(targetDate), anyList());

        ManualBaseballDataRequiredException first = assertThrows(
                ManualBaseballDataRequiredException.class,
                () -> predictionService.getMatchesByDate(targetDate));
        ManualBaseballDataRequiredException second = assertThrows(
                ManualBaseballDataRequiredException.class,
                () -> predictionService.getMatchesByDate(targetDate));

        assertThat(first.getData()).isEqualTo(manualRequest);
        assertThat(second.getData()).isEqualTo(manualRequest);
        verify(gameRepository, times(1)).findCanonicalRangeProjectionByGameDate(eq(targetDate), anyList());
        verify(baseballDataIntegrityGuard, times(1))
                .ensurePredictionDateMatches(eq("prediction.matches_by_date"), eq(targetDate), anyList());
    }

    @Test
    void getMatchesByDateShouldReturnDisplayableRowsWhenSomeRowsNeedManualData() {
        LocalDate targetDate = LocalDate.of(2026, 6, 26);
        MatchRangeProjection displayable = buildRangeMatch("20260626HTOB0", targetDate, "HT", "OB", 20260, 0, null);
        MatchRangeProjection incomplete = buildRangeMatch("20260626HHSK0", targetDate, "HH", "SK", 20260, 0, null);
        ManualBaseballDataRequest manualRequest = new ManualBaseballDataRequest(
                "prediction.matches_by_date",
                List.of(
                        new ManualBaseballDataMissingItem(
                                "game_status",
                                "경기 상태",
                                "과거 경기 상태가 종료 기준으로 확정되지 않았습니다.",
                                "SCHEDULED, COMPLETED, CANCELLED 등"),
                        new ManualBaseballDataMissingItem(
                                "final_score",
                                "최종 점수",
                                "과거 경기의 최종 점수가 비어 있습니다.",
                                "home_score, away_score")),
                "다음 야구 데이터가 필요합니다: 경기 ID=20260626HHSK0, 경기 상태, 최종 점수",
                true);

        when(gameRepository.findCanonicalRangeProjectionByGameDate(eq(targetDate), anyList()))
                .thenReturn(List.of(displayable, incomplete));
        doThrow(new ManualBaseballDataRequiredException(manualRequest))
                .when(baseballDataIntegrityGuard)
                .ensurePredictionDateMatches(eq("prediction.matches_by_date"), eq(targetDate), anyList());
        doReturn(null)
                .when(baseballDataIntegrityGuard)
                .findMatchProjectionManualDataRequest(eq("prediction.matches_by_date"), any(MatchRangeProjection.class));
        doReturn(manualRequest)
                .when(baseballDataIntegrityGuard)
                .findMatchProjectionManualDataRequest(
                        eq("prediction.matches_by_date"),
                        eq(incomplete));

        List<MatchDto> first = predictionService.getMatchesByDate(targetDate);
        List<MatchDto> second = predictionService.getMatchesByDate(targetDate);

        assertThat(first).extracting(MatchDto::getGameId).containsExactly("20260626HTOB0");
        assertThat(second).extracting(MatchDto::getGameId).containsExactly("20260626HTOB0");
        verify(gameRepository, times(2)).findCanonicalRangeProjectionByGameDate(eq(targetDate), anyList());
        verify(baseballDataIntegrityGuard, times(2))
                .ensurePredictionDateMatches(eq("prediction.matches_by_date"), eq(targetDate), anyList());
        verify(baseballDataIntegrityGuard, times(2)).findMatchProjectionManualDataRequest(
                eq("prediction.matches_by_date"),
                eq(incomplete));
    }

    @Test
    void getMatchesByDateShouldCacheSuccessfulDateForSameRequest() {
        LocalDate targetDate = LocalDate.of(2026, 6, 18);
        MatchRangeProjection canonical = buildRangeMatch("20260618HHNC0", targetDate, "HH", "NC", 20260, 0, null);

        when(gameRepository.findCanonicalRangeProjectionByGameDate(eq(targetDate), anyList()))
                .thenReturn(List.of(canonical));

        List<MatchDto> first = predictionService.getMatchesByDate(targetDate);
        List<MatchDto> second = predictionService.getMatchesByDate(targetDate);

        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
        assertThat(second.get(0).getGameId()).isEqualTo("20260618HHNC0");
        verify(gameRepository, times(1)).findCanonicalRangeProjectionByGameDate(eq(targetDate), anyList());
        verify(baseballDataIntegrityGuard, times(1))
                .ensurePredictionDateMatches(eq("prediction.matches_by_date"), eq(targetDate), anyList());
    }

    @Test
    void getMatchDayNavigationShouldReturnDisplayableRowsWhenSomeRowsNeedManualData() {
        LocalDate targetDate = LocalDate.of(2026, 6, 26);
        MatchRangeProjection displayable = buildRangeMatch("20260626WONC0", targetDate, "WO", "NC", 20260, 0, null);
        MatchRangeProjection incomplete = buildRangeMatch("20260626HHSK0", targetDate, "HH", "SK", 20260, 0, null);
        CanonicalAdjacentGameDatesProjection adjacentDates = mock(CanonicalAdjacentGameDatesProjection.class);
        ManualBaseballDataRequest manualRequest = new ManualBaseballDataRequest(
                "prediction.matches_by_date",
                List.of(new ManualBaseballDataMissingItem(
                        "final_score",
                        "최종 점수",
                        "과거 경기의 최종 점수가 비어 있습니다.",
                        "home_score, away_score")),
                "다음 야구 데이터가 필요합니다: 경기 ID=20260626HHSK0, 최종 점수",
                true);

        when(gameRepository.findCanonicalRangeProjectionByGameDate(eq(targetDate), anyList()))
                .thenReturn(List.of(displayable, incomplete));
        when(gameRepository.findCanonicalAdjacentGameDates(eq(targetDate), anyList()))
                .thenReturn(adjacentDates);
        when(adjacentDates.getPrevDate()).thenReturn(LocalDate.of(2026, 6, 25));
        when(adjacentDates.getNextDate()).thenReturn(LocalDate.of(2026, 6, 27));
        doThrow(new ManualBaseballDataRequiredException(manualRequest))
                .when(baseballDataIntegrityGuard)
                .ensurePredictionDateMatches(eq("prediction.matches_by_date"), eq(targetDate), anyList());
        doReturn(null)
                .when(baseballDataIntegrityGuard)
                .findMatchProjectionManualDataRequest(eq("prediction.matches_by_date"), any(MatchRangeProjection.class));
        doReturn(manualRequest)
                .when(baseballDataIntegrityGuard)
                .findMatchProjectionManualDataRequest(
                        eq("prediction.matches_by_date"),
                        eq(incomplete));

        MatchDayNavigationResponseDto response = predictionService.getMatchDayNavigation(targetDate);

        assertThat(response.getDate()).isEqualTo(targetDate);
        assertThat(response.getGames()).extracting(MatchDto::getGameId).containsExactly("20260626WONC0");
        assertThat(response.getPrevDate()).isEqualTo(LocalDate.of(2026, 6, 25));
        assertThat(response.getNextDate()).isEqualTo(LocalDate.of(2026, 6, 27));
        verify(baseballDataIntegrityGuard).findMatchProjectionManualDataRequest(
                eq("prediction.matches_by_date"),
                eq(incomplete));
    }

    @Test
    void getMatchDayNavigationShouldCacheManualDataRequiredForSameDate() {
        LocalDate targetDate = LocalDate.of(2026, 6, 18);
        MatchRangeProjection incomplete = buildRangeMatch(
                "20260618HHNC0",
                targetDate,
                "HH",
                "NC",
                20260,
                0,
                null);
        ManualBaseballDataRequest manualRequest = new ManualBaseballDataRequest(
                "prediction.matches_by_date",
                List.of(new ManualBaseballDataMissingItem(
                        "season_league_context",
                        "시즌/리그 컨텍스트",
                        "경기의 시즌/리그 컨텍스트가 비어 있습니다.",
                        "season_id, league_type")),
                "운영자가 경기 시즌 컨텍스트를 수동으로 제공해야 합니다.",
                true);

        when(gameRepository.findCanonicalRangeProjectionByGameDate(eq(targetDate), anyList()))
                .thenReturn(List.of(incomplete));
        doThrow(new ManualBaseballDataRequiredException(manualRequest))
                .when(baseballDataIntegrityGuard)
                .ensurePredictionDateMatches(eq("prediction.matches_by_date"), eq(targetDate), anyList());

        ManualBaseballDataRequiredException first = assertThrows(
                ManualBaseballDataRequiredException.class,
                () -> predictionService.getMatchDayNavigation(targetDate));
        ManualBaseballDataRequiredException second = assertThrows(
                ManualBaseballDataRequiredException.class,
                () -> predictionService.getMatchDayNavigation(targetDate));

        assertThat(first.getData()).isEqualTo(manualRequest);
        assertThat(second.getData()).isEqualTo(manualRequest);
        verify(gameRepository, times(1)).findCanonicalRangeProjectionByGameDate(eq(targetDate), anyList());
        verify(gameRepository, never()).findCanonicalAdjacentGameDates(eq(targetDate), anyList());
        verify(baseballDataIntegrityGuard, times(1))
                .ensurePredictionDateMatches(eq("prediction.matches_by_date"), eq(targetDate), anyList());
    }
}
