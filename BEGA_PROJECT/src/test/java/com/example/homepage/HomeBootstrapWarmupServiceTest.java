package com.example.homepage;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HomeBootstrapWarmupServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final Duration WARMUP_SECTION_TIMEOUT = Duration.ofSeconds(8);

    @Mock
    private HomePageFacadeService homePageFacadeService;

    @Test
    @DisplayName("warm-up이 활성화되면 서버 기준 오늘 bootstrap을 미리 조회한다")
    void warmupTodayBootstrapLoadsTodayWhenEnabled() {
        HomeBootstrapWarmupService service =
                new HomeBootstrapWarmupService(homePageFacadeService, FIXED_CLOCK, true);
        when(homePageFacadeService.refreshBootstrap(LocalDate.of(2026, 5, 15), WARMUP_SECTION_TIMEOUT))
                .thenReturn(completeBootstrapResponse());

        service.warmupTodayBootstrap();

        verify(homePageFacadeService).refreshBootstrap(LocalDate.of(2026, 5, 15), WARMUP_SECTION_TIMEOUT);
        verify(homePageFacadeService).getRankingSnapshot(LocalDate.of(2026, 5, 15), null);
        verifyNoMoreInteractions(homePageFacadeService);
    }

    @Test
    @DisplayName("warm-up이 비활성화되면 bootstrap을 조회하지 않는다")
    void warmupTodayBootstrapSkipsWhenDisabled() {
        HomeBootstrapWarmupService service =
                new HomeBootstrapWarmupService(homePageFacadeService, FIXED_CLOCK, false);

        service.warmupTodayBootstrap();

        verifyNoInteractions(homePageFacadeService);
    }

    @Test
    @DisplayName("warm-up 첫 응답이 partial이면 하위 cache warm 이후 한 번 더 refresh한다")
    void warmupTodayBootstrapRetriesOnceWhenFirstResponseIsPartial() {
        LocalDate today = LocalDate.of(2026, 5, 15);
        HomeBootstrapWarmupService service =
                new HomeBootstrapWarmupService(homePageFacadeService, FIXED_CLOCK, true, Duration.ZERO);
        when(homePageFacadeService.refreshBootstrap(today, WARMUP_SECTION_TIMEOUT))
                .thenReturn(partialBootstrapResponse())
                .thenReturn(completeBootstrapResponse());

        service.warmupTodayBootstrap();

        verify(homePageFacadeService, org.mockito.Mockito.times(2)).refreshBootstrap(today, WARMUP_SECTION_TIMEOUT);
        verify(homePageFacadeService).getRankingSnapshot(today, null);
        verifyNoMoreInteractions(homePageFacadeService);
    }

    @Test
    @DisplayName("warm-up partial 응답이 반복되어도 최대 2회까지만 refresh한다")
    void warmupTodayBootstrapDoesNotRetryMoreThanTwiceWhenPartialPersists() {
        LocalDate today = LocalDate.of(2026, 5, 15);
        HomeBootstrapWarmupService service =
                new HomeBootstrapWarmupService(homePageFacadeService, FIXED_CLOCK, true, Duration.ZERO);
        when(homePageFacadeService.refreshBootstrap(today, WARMUP_SECTION_TIMEOUT))
                .thenReturn(partialBootstrapResponse())
                .thenReturn(partialBootstrapResponse());

        service.warmupTodayBootstrap();

        verify(homePageFacadeService, org.mockito.Mockito.times(2)).refreshBootstrap(today, WARMUP_SECTION_TIMEOUT);
        verify(homePageFacadeService).getRankingSnapshot(today, null);
        verifyNoMoreInteractions(homePageFacadeService);
    }

    @Test
    @DisplayName("warm-up은 section 목록이 있으면 fallback/timeout flag가 false여도 partial로 재시도한다")
    void warmupTodayBootstrapRetriesWhenSectionListsArePresentEvenIfFlagsAreFalse() {
        LocalDate today = LocalDate.of(2026, 5, 15);
        HomeBootstrapWarmupService service =
                new HomeBootstrapWarmupService(homePageFacadeService, FIXED_CLOCK, true, Duration.ZERO);
        when(homePageFacadeService.refreshBootstrap(today, WARMUP_SECTION_TIMEOUT))
                .thenReturn(partialBootstrapResponseWithFalseFlags())
                .thenReturn(completeBootstrapResponse());

        service.warmupTodayBootstrap();

        verify(homePageFacadeService, org.mockito.Mockito.times(2)).refreshBootstrap(today, WARMUP_SECTION_TIMEOUT);
        verify(homePageFacadeService).getRankingSnapshot(today, null);
        verifyNoMoreInteractions(homePageFacadeService);
    }

    @Test
    @DisplayName("ranking snapshot warm-up 실패는 bootstrap warm-up을 실패시키지 않는다")
    void warmupTodayBootstrapDoesNotPropagateRankingSnapshotWarmupFailure() {
        LocalDate today = LocalDate.of(2026, 5, 15);
        HomeBootstrapWarmupService service =
                new HomeBootstrapWarmupService(homePageFacadeService, FIXED_CLOCK, true, Duration.ZERO);
        when(homePageFacadeService.refreshBootstrap(today, WARMUP_SECTION_TIMEOUT))
                .thenReturn(completeBootstrapResponse());
        when(homePageFacadeService.getRankingSnapshot(today, null))
                .thenThrow(new IllegalStateException("ranking cache unavailable"));

        service.warmupTodayBootstrap();

        verify(homePageFacadeService).refreshBootstrap(today, WARMUP_SECTION_TIMEOUT);
        verify(homePageFacadeService).getRankingSnapshot(today, null);
        verifyNoMoreInteractions(homePageFacadeService);
    }

    @Test
    @DisplayName("ranking warm-up이 비활성화되면 bootstrap cache만 갱신한다")
    void warmupTodayBootstrapSkipsRankingSnapshotWhenRankingWarmupDisabled() {
        LocalDate today = LocalDate.of(2026, 5, 15);
        HomeBootstrapWarmupService service = new HomeBootstrapWarmupService(
                homePageFacadeService,
                FIXED_CLOCK,
                true,
                Duration.ZERO,
                WARMUP_SECTION_TIMEOUT,
                false,
                2);
        when(homePageFacadeService.refreshBootstrap(today, WARMUP_SECTION_TIMEOUT))
                .thenReturn(completeBootstrapResponse());

        service.warmupTodayBootstrap();

        verify(homePageFacadeService).refreshBootstrap(today, WARMUP_SECTION_TIMEOUT);
        verifyNoMoreInteractions(homePageFacadeService);
    }

    @Test
    @DisplayName("warm-up max-attempts가 1이면 partial 응답에도 재시도하지 않는다")
    void warmupTodayBootstrapHonorsMaxAttemptsThrottle() {
        LocalDate today = LocalDate.of(2026, 5, 15);
        HomeBootstrapWarmupService service = new HomeBootstrapWarmupService(
                homePageFacadeService,
                FIXED_CLOCK,
                true,
                Duration.ZERO,
                WARMUP_SECTION_TIMEOUT,
                true,
                1);
        when(homePageFacadeService.refreshBootstrap(today, WARMUP_SECTION_TIMEOUT))
                .thenReturn(partialBootstrapResponse());

        service.warmupTodayBootstrap();

        verify(homePageFacadeService).refreshBootstrap(today, WARMUP_SECTION_TIMEOUT);
        verify(homePageFacadeService).getRankingSnapshot(today, null);
        verifyNoMoreInteractions(homePageFacadeService);
    }

    private HomeBootstrapResponseDto completeBootstrapResponse() {
        return HomeBootstrapResponseDto.builder()
                .selectedDate("2026-05-15")
                .loadState(HomeBootstrapLoadStateDto.builder()
                        .isFallback(false)
                        .timedOut(false)
                        .timedOutSections(List.of())
                        .failedSections(List.of())
                        .build())
                .build();
    }

    private HomeBootstrapResponseDto partialBootstrapResponse() {
        return HomeBootstrapResponseDto.builder()
                .selectedDate("2026-05-15")
                .loadState(HomeBootstrapLoadStateDto.builder()
                        .isFallback(true)
                        .timedOut(true)
                        .timedOutSections(List.of("scheduledGamesWindow"))
                        .failedSections(List.of("scheduledGamesWindow"))
                        .build())
                .build();
    }

    private HomeBootstrapResponseDto partialBootstrapResponseWithFalseFlags() {
        return HomeBootstrapResponseDto.builder()
                .selectedDate("2026-05-15")
                .loadState(HomeBootstrapLoadStateDto.builder()
                        .isFallback(false)
                        .timedOut(false)
                        .timedOutSections(List.of("scheduledGamesWindow"))
                        .failedSections(List.of("scheduledGamesWindow"))
                        .build())
                .build();
    }
}
