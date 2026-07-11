package com.example.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.exception.NotFoundBusinessException;
import com.example.kbo.validation.ManualBaseballDataMissingItem;
import com.example.kbo.validation.ManualBaseballDataRequest;
import com.example.kbo.validation.ManualBaseballDataRequiredException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
    void getBootstrapShouldRecordResourceSuccessMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        service = new PredictionBootstrapService(predictionService, executor, Duration.ofMillis(500), registry);
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
                .awayVotes(0L)
                .totalVotes(1L)
                .homePercentage(100)
                .awayPercentage(0)
                .build();

        when(predictionService.getMatchDayNavigation(date)).thenReturn(schedule);
        when(predictionService.getGameDetail(gameId)).thenReturn(detail);
        when(predictionService.getVoteStatus(gameId)).thenReturn(voteStatus);

        PredictionBootstrapResponseDto response = service.getBootstrap(date, gameId);

        assertThat(response.detail()).isNotNull();
        assertThat(response.detail().ok()).isTrue();
        assertThat(response.voteStatus()).isNotNull();
        assertThat(response.voteStatus().ok()).isTrue();
        assertThat(resourceTimerCount(registry, "detail", "success")).isEqualTo(1L);
        assertThat(resourceTimerCount(registry, "votestatus", "success")).isEqualTo(1L);
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
    void getBootstrapShouldTimeoutSlowDetailAndKeepVoteStatus() {
        executor.shutdownNow();
        executor = Executors.newFixedThreadPool(2);
        service = new PredictionBootstrapService(predictionService, executor, Duration.ofMillis(50));
        LocalDate date = LocalDate.of(2026, 6, 7);
        String gameId = "20260607HHLT0";
        MatchDayNavigationResponseDto schedule = schedule(date, List.of(match(gameId)));
        PredictionResponseDto voteStatus = PredictionResponseDto.builder()
                .gameId(gameId)
                .homeVotes(1L)
                .awayVotes(0L)
                .totalVotes(1L)
                .homePercentage(100)
                .awayPercentage(0)
                .build();

        when(predictionService.getMatchDayNavigation(date)).thenReturn(schedule);
        when(predictionService.getGameDetail(gameId)).thenAnswer(invocation -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return GameDetailDto.builder().gameId(gameId).build();
        });
        when(predictionService.getVoteStatus(gameId)).thenReturn(voteStatus);

        PredictionBootstrapResponseDto response = service.getBootstrap(date, gameId);

        assertThat(response.schedule()).isSameAs(schedule);
        assertThat(response.selectedGameFound()).isTrue();
        assertThat(response.detail()).isNotNull();
        assertThat(response.detail().ok()).isFalse();
        assertThat(response.detail().error().code()).isEqualTo("PREDICTION_BOOTSTRAP_RESOURCE_TIMEOUT");
        assertThat(response.detail().error().status()).isEqualTo(504);
        assertThat(response.voteStatus()).isNotNull();
        assertThat(response.voteStatus().ok()).isTrue();
        assertThat(response.voteStatus().data()).isSameAs(voteStatus);
    }

    @Test
    void getBootstrapShouldShareOneResourceDeadlineAcrossDetailAndVoteStatus() {
        executor.shutdownNow();
        executor = Executors.newFixedThreadPool(2);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        service = new PredictionBootstrapService(predictionService, executor, Duration.ofMillis(60), registry);
        LocalDate date = LocalDate.of(2026, 6, 7);
        String gameId = "20260607HHLT0";
        MatchDayNavigationResponseDto schedule = schedule(date, List.of(match(gameId)));

        when(predictionService.getMatchDayNavigation(date)).thenReturn(schedule);
        when(predictionService.getGameDetail(gameId)).thenAnswer(invocation -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return GameDetailDto.builder().gameId(gameId).build();
        });
        when(predictionService.getVoteStatus(gameId)).thenAnswer(invocation -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return PredictionResponseDto.builder().gameId(gameId).build();
        });

        long startedAt = System.nanoTime();
        PredictionBootstrapResponseDto response = service.getBootstrap(date, gameId);
        long elapsedMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        assertThat(response.schedule()).isSameAs(schedule);
        assertThat(response.detail()).isNotNull();
        assertThat(response.detail().ok()).isFalse();
        assertThat(response.detail().error().code()).isEqualTo("PREDICTION_BOOTSTRAP_RESOURCE_TIMEOUT");
        assertThat(response.voteStatus()).isNotNull();
        assertThat(response.voteStatus().ok()).isFalse();
        assertThat(response.voteStatus().error().code()).isEqualTo("PREDICTION_BOOTSTRAP_RESOURCE_TIMEOUT");
        assertThat(elapsedMs).isLessThan(180L);
        assertThat(resourceTimerCount(registry, "detail", "timeout")).isEqualTo(1L);
        assertThat(resourceTimerCount(registry, "votestatus", "timeout")).isEqualTo(1L);
    }

    @Test
    void getBootstrapShouldBoundConcurrentResourceLoadersAndFailFastWhenBusy() {
        executor.shutdownNow();
        executor = Executors.newFixedThreadPool(2);
        service = new PredictionBootstrapService(
                predictionService,
                executor,
                Duration.ofMillis(80),
                new SimpleMeterRegistry(),
                1,
                Duration.ofMillis(10));
        LocalDate date = LocalDate.of(2026, 6, 7);
        String gameId = "20260607HHLT0";
        CountDownLatch releaseLoader = new CountDownLatch(1);
        AtomicInteger activeLoaders = new AtomicInteger();
        AtomicInteger maxActiveLoaders = new AtomicInteger();
        when(predictionService.getMatchDayNavigation(date)).thenReturn(schedule(date, List.of(match(gameId))));
        when(predictionService.getGameDetail(gameId)).thenAnswer(invocation -> {
            blockResourceLoader(releaseLoader, activeLoaders, maxActiveLoaders);
            return GameDetailDto.builder().gameId(gameId).build();
        });
        when(predictionService.getVoteStatus(gameId)).thenAnswer(invocation -> {
            blockResourceLoader(releaseLoader, activeLoaders, maxActiveLoaders);
            return PredictionResponseDto.builder().gameId(gameId).build();
        });

        PredictionBootstrapResponseDto response;
        try {
            response = service.getBootstrap(date, gameId);
        } finally {
            releaseLoader.countDown();
        }

        assertThat(maxActiveLoaders).hasValue(1);
        assertThat(List.of(response.detail().error().code(), response.voteStatus().error().code()))
                .containsExactlyInAnyOrder(
                        "PREDICTION_BOOTSTRAP_RESOURCE_TIMEOUT",
                        "PREDICTION_BOOTSTRAP_RESOURCE_BUSY");
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

    @Test
    void getBootstrapShouldNegativeCacheManualDataRequiredScheduleFailure() {
        LocalDate date = LocalDate.of(2026, 6, 18);
        ManualBaseballDataRequiredException manualDataRequired =
                new ManualBaseballDataRequiredException(new ManualBaseballDataRequest(
                        "prediction.matches_by_date",
                        List.of(new ManualBaseballDataMissingItem(
                                "season_league_context",
                                "시즌/리그 컨텍스트",
                                "경기의 시즌/리그 컨텍스트가 비어 있습니다.",
                                "season_id, league_type")),
                        "다음 야구 데이터가 필요합니다: 경기 ID=20260618HHNC0",
                        true));

        when(predictionService.getMatchDayNavigation(date)).thenThrow(manualDataRequired);

        assertThatThrownBy(() -> service.getBootstrap(date, null))
                .isInstanceOf(ManualBaseballDataRequiredException.class);
        assertThatThrownBy(() -> service.getBootstrap(date, null))
                .isInstanceOf(ManualBaseballDataRequiredException.class);

        verify(predictionService, times(1)).getMatchDayNavigation(date);
    }

    @Test
    void getBootstrapShouldCoalesceConcurrentRequestsForSameDateAndGameId() throws Exception {
        executor.shutdownNow();
        executor = Executors.newFixedThreadPool(2);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        service = new PredictionBootstrapService(predictionService, executor, Duration.ofMillis(500), registry);

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
                .homeVotes(3L)
                .awayVotes(1L)
                .totalVotes(4L)
                .homePercentage(75)
                .awayPercentage(25)
                .build();
        CountDownLatch firstScheduleCallStarted = new CountDownLatch(1);
        CountDownLatch secondCallerStarted = new CountDownLatch(1);
        CountDownLatch releaseSchedule = new CountDownLatch(1);

        when(predictionService.getMatchDayNavigation(date)).thenAnswer(invocation -> {
            firstScheduleCallStarted.countDown();
            assertThat(secondCallerStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(releaseSchedule.await(1, TimeUnit.SECONDS)).isTrue();
            return schedule;
        });
        when(predictionService.getGameDetail(gameId)).thenReturn(detail);
        when(predictionService.getVoteStatus(gameId)).thenReturn(voteStatus);

        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<PredictionBootstrapResponseDto> first =
                    callers.submit(() -> service.getBootstrap(date, gameId));
            assertThat(firstScheduleCallStarted.await(1, TimeUnit.SECONDS)).isTrue();
            Future<PredictionBootstrapResponseDto> second = callers.submit(() -> {
                secondCallerStarted.countDown();
                return service.getBootstrap(date, " " + gameId + " ");
            });
            TimeUnit.MILLISECONDS.sleep(100);
            releaseSchedule.countDown();

            PredictionBootstrapResponseDto firstResponse = first.get(2, TimeUnit.SECONDS);
            PredictionBootstrapResponseDto secondResponse = second.get(2, TimeUnit.SECONDS);

            assertThat(secondResponse).isSameAs(firstResponse);
            assertThat(firstResponse.detail()).isNotNull();
            assertThat(firstResponse.detail().data()).isSameAs(detail);
            assertThat(firstResponse.voteStatus()).isNotNull();
            assertThat(firstResponse.voteStatus().data()).isSameAs(voteStatus);
        } finally {
            releaseSchedule.countDown();
            callers.shutdownNow();
        }

        verify(predictionService, times(1)).getMatchDayNavigation(date);
        verify(predictionService, times(1)).getGameDetail(gameId);
        verify(predictionService, times(1)).getVoteStatus(gameId);
        assertThat(counterCount(registry, "inflight", "miss")).isEqualTo(1.0d);
        assertThat(counterCount(registry, "inflight", "hit")).isEqualTo(1.0d);
    }

    private double counterCount(SimpleMeterRegistry registry, String operation, String result) {
        return registry.get("prediction_bootstrap_events_total")
                .tag("operation", operation)
                .tag("result", result)
                .counter()
                .count();
    }

    private long resourceTimerCount(SimpleMeterRegistry registry, String resource, String result) {
        return registry.get("prediction_bootstrap_resource_duration_seconds")
                .tag("resource", resource)
                .tag("result", result)
                .timer()
                .count();
    }

    private void blockResourceLoader(
            CountDownLatch releaseLoader,
            AtomicInteger activeLoaders,
            AtomicInteger maxActiveLoaders) {
        int active = activeLoaders.incrementAndGet();
        maxActiveLoaders.accumulateAndGet(active, Math::max);
        try {
            while (releaseLoader.getCount() > 0) {
                try {
                    releaseLoader.await(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ignored) {
                    // Simulate a JDBC driver that does not abort an in-flight operation on interruption.
                }
            }
        } finally {
            activeLoaders.decrementAndGet();
        }
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
