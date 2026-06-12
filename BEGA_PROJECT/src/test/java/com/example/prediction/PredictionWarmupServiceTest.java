package com.example.prediction;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PredictionWarmupServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-07T00:00:00Z"),
            ZoneId.of("Asia/Seoul"));

    @Test
    void warmupTodayPredictionDataShouldDoNothingWhenDisabled() {
        PredictionService predictionService = Mockito.mock(PredictionService.class);
        PredictionWarmupService service = new PredictionWarmupService(
                predictionService,
                FIXED_CLOCK,
                false,
                true,
                true);

        service.warmupTodayPredictionData();

        verifyNoInteractions(predictionService);
    }

    @Test
    void warmupTodayPredictionDataShouldWarmScheduleDetailAndVoteStatus() {
        PredictionService predictionService = Mockito.mock(PredictionService.class);
        PredictionWarmupService service = new PredictionWarmupService(
                predictionService,
                FIXED_CLOCK,
                true,
                true,
                true);
        LocalDate today = LocalDate.of(2026, 6, 7);
        MatchDayNavigationResponseDto schedule = schedule(today, List.of(
                match("20260607HHLT0"),
                match("20260607LGKT0")));
        when(predictionService.getMatchDayNavigation(today)).thenReturn(schedule);

        service.warmupTodayPredictionData();

        verify(predictionService).getMatchDayNavigation(today);
        verify(predictionService).getGameDetail("20260607HHLT0");
        verify(predictionService).getGameDetail("20260607LGKT0");
        verify(predictionService).getVoteStatus("20260607HHLT0");
        verify(predictionService).getVoteStatus("20260607LGKT0");
    }

    @Test
    void warmupTodayPredictionDataShouldRespectResourceFlags() {
        PredictionService predictionService = Mockito.mock(PredictionService.class);
        PredictionWarmupService service = new PredictionWarmupService(
                predictionService,
                FIXED_CLOCK,
                true,
                false,
                true);
        LocalDate today = LocalDate.of(2026, 6, 7);
        when(predictionService.getMatchDayNavigation(today))
                .thenReturn(schedule(today, List.of(match("20260607HHLT0"))));

        service.warmupTodayPredictionData();

        verify(predictionService).getMatchDayNavigation(today);
        verify(predictionService, never()).getGameDetail("20260607HHLT0");
        verify(predictionService).getVoteStatus("20260607HHLT0");
    }

    @Test
    void warmupTodayPredictionDataShouldContinueWhenOneResourceFails() {
        PredictionService predictionService = Mockito.mock(PredictionService.class);
        PredictionWarmupService service = new PredictionWarmupService(
                predictionService,
                FIXED_CLOCK,
                true,
                true,
                true);
        LocalDate today = LocalDate.of(2026, 6, 7);
        String firstGameId = "20260607HHLT0";
        String secondGameId = "20260607LGKT0";
        when(predictionService.getMatchDayNavigation(today))
                .thenReturn(schedule(today, List.of(match(firstGameId), match(secondGameId))));
        when(predictionService.getGameDetail(firstGameId))
                .thenThrow(new IllegalStateException("detail warmup failed"));
        when(predictionService.getVoteStatus(secondGameId))
                .thenThrow(new IllegalStateException("vote warmup failed"));

        service.warmupTodayPredictionData();

        verify(predictionService).getMatchDayNavigation(today);
        verify(predictionService).getGameDetail(firstGameId);
        verify(predictionService).getVoteStatus(firstGameId);
        verify(predictionService).getGameDetail(secondGameId);
        verify(predictionService).getVoteStatus(secondGameId);
    }

    private MatchDayNavigationResponseDto schedule(LocalDate date, List<MatchDto> games) {
        return new MatchDayNavigationResponseDto(
                date,
                games,
                null,
                null,
                false,
                false);
    }

    private MatchDto match(String gameId) {
        return MatchDto.builder()
                .gameId(gameId)
                .gameDate(LocalDate.of(2026, 6, 7))
                .homeTeam("HH")
                .awayTeam("LT")
                .stadium("Jamsil")
                .build();
    }
}
