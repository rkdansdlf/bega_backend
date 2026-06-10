package com.example.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.exception.NotFoundBusinessException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PredictionBootstrapServiceTest {

    private PredictionService predictionService;
    private ExecutorService executor;
    private PredictionBootstrapService service;

    @BeforeEach
    void setUp() {
        predictionService = Mockito.mock(PredictionService.class);
        executor = Executors.newSingleThreadExecutor();
        service = new PredictionBootstrapService(predictionService, executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void getBootstrapShouldReturnScheduleOnlyWhenGameIdIsMissing() {
        LocalDate date = LocalDate.of(2026, 6, 7);
        MatchDayNavigationResponseDto schedule = schedule(date, List.of(match("20260607HHLT0")));
        when(predictionService.getMatchDayNavigation(date)).thenReturn(schedule);

        PredictionBootstrapResponseDto response = service.getBootstrap(date, null);

        assertThat(response.schedule()).isSameAs(schedule);
        assertThat(response.selectedGameId()).isNull();
        assertThat(response.selectedGameFound()).isFalse();
        assertThat(response.detail()).isNull();
        assertThat(response.voteStatus()).isNull();
        verify(predictionService).getMatchDayNavigation(date);
        verify(predictionService, never()).getGameDetail("20260607HHLT0");
        verify(predictionService, never()).getVoteStatus("20260607HHLT0");
    }

    @Test
    void getBootstrapShouldLoadDetailAndVoteStatusWhenGameIdIsFound() {
        LocalDate date = LocalDate.of(2026, 6, 7);
        String gameId = "20260607HHLT0";
        MatchDayNavigationResponseDto schedule = schedule(date, List.of(match(gameId)));
        GameDetailDto detail = GameDetailDto.builder()
                .gameId(gameId)
                .homeTeam("HH")
                .awayTeam("LT")
                .build();
        PredictionResponseDto voteStatus = PredictionResponseDto.builder()
                .gameId(gameId)
                .homeVotes(1L)
                .awayVotes(2L)
                .totalVotes(3L)
                .homePercentage(33)
                .awayPercentage(67)
                .build();

        when(predictionService.getMatchDayNavigation(date)).thenReturn(schedule);
        when(predictionService.getGameDetail(gameId)).thenReturn(detail);
        when(predictionService.getVoteStatus(gameId)).thenReturn(voteStatus);

        PredictionBootstrapResponseDto response = service.getBootstrap(date, gameId);

        assertThat(response.selectedGameId()).isEqualTo(gameId);
        assertThat(response.selectedGameFound()).isTrue();
        assertThat(response.detail()).isNotNull();
        assertThat(response.detail().ok()).isTrue();
        assertThat(response.detail().data()).isSameAs(detail);
        assertThat(response.voteStatus()).isNotNull();
        assertThat(response.voteStatus().ok()).isTrue();
        assertThat(response.voteStatus().data()).isSameAs(voteStatus);
    }

    @Test
    void getBootstrapShouldKeepScheduleWhenDetailFails() {
        LocalDate date = LocalDate.of(2026, 6, 7);
        String gameId = "20260607HHLT0";
        MatchDayNavigationResponseDto schedule = schedule(date, List.of(match(gameId)));
        PredictionResponseDto voteStatus = PredictionResponseDto.builder()
                .gameId(gameId)
                .homeVotes(0L)
                .awayVotes(0L)
                .totalVotes(0L)
                .homePercentage(0)
                .awayPercentage(0)
                .build();

        when(predictionService.getMatchDayNavigation(date)).thenReturn(schedule);
        when(predictionService.getGameDetail(gameId))
                .thenThrow(new NotFoundBusinessException("MATCH_NOT_FOUND", "경기 정보를 찾을 수 없습니다."));
        when(predictionService.getVoteStatus(gameId)).thenReturn(voteStatus);

        PredictionBootstrapResponseDto response = service.getBootstrap(date, gameId);

        assertThat(response.schedule()).isSameAs(schedule);
        assertThat(response.selectedGameFound()).isTrue();
        assertThat(response.detail()).isNotNull();
        assertThat(response.detail().ok()).isFalse();
        assertThat(response.detail().error().code()).isEqualTo("MATCH_NOT_FOUND");
        assertThat(response.detail().error().status()).isEqualTo(404);
        assertThat(response.voteStatus()).isNotNull();
        assertThat(response.voteStatus().ok()).isTrue();
    }

    @Test
    void getBootstrapShouldNotLoadDetailWhenGameIdIsNotInSchedule() {
        LocalDate date = LocalDate.of(2026, 6, 7);
        MatchDayNavigationResponseDto schedule = schedule(date, List.of(match("20260607HHLT0")));
        when(predictionService.getMatchDayNavigation(date)).thenReturn(schedule);

        PredictionBootstrapResponseDto response = service.getBootstrap(date, "20260607LGKT0");

        assertThat(response.selectedGameId()).isEqualTo("20260607LGKT0");
        assertThat(response.selectedGameFound()).isFalse();
        assertThat(response.detail()).isNull();
        assertThat(response.voteStatus()).isNull();
        verify(predictionService, never()).getGameDetail("20260607LGKT0");
        verify(predictionService, never()).getVoteStatus("20260607LGKT0");
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
