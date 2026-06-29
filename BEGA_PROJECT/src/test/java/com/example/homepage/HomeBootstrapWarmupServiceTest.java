package com.example.homepage;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.kbo.validation.ManualBaseballDataMissingItem;
import com.example.kbo.validation.ManualBaseballDataRequest;
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
    private static final Duration WARMUP_SECTION_TIMEOUT = Duration.ofSeconds(3);

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
    @DisplayName("warm-up 첫 응답이 partial이면 같은 tick에서 재시도나 ranking warm-up을 하지 않는다")
    void warmupTodayBootstrapDoesNotRetryOrWarmRankingWhenFirstResponseIsPartial() {
        LocalDate today = LocalDate.of(2026, 5, 15);
        HomeBootstrapWarmupService service =
                new HomeBootstrapWarmupService(homePageFacadeService, FIXED_CLOCK, true, Duration.ZERO);
        when(homePageFacadeService.refreshBootstrap(today, WARMUP_SECTION_TIMEOUT))
                .thenReturn(partialBootstrapResponse());

        service.warmupTodayBootstrap();

        verify(homePageFacadeService).refreshBootstrap(today, WARMUP_SECTION_TIMEOUT);
        verifyNoMoreInteractions(homePageFacadeService);
    }

    @Test
    @DisplayName("warm-up partial 응답이 반복 가능한 상태여도 같은 tick에서는 한 번만 refresh한다")
    void warmupTodayBootstrapRefreshesOnceWhenPartialPersists() {
        LocalDate today = LocalDate.of(2026, 5, 15);
        HomeBootstrapWarmupService service =
                new HomeBootstrapWarmupService(homePageFacadeService, FIXED_CLOCK, true, Duration.ZERO);
        when(homePageFacadeService.refreshBootstrap(today, WARMUP_SECTION_TIMEOUT))
                .thenReturn(partialBootstrapResponse());

        service.warmupTodayBootstrap();

        verify(homePageFacadeService).refreshBootstrap(today, WARMUP_SECTION_TIMEOUT);
        verifyNoMoreInteractions(homePageFacadeService);
    }

    @Test
    @DisplayName("warm-up은 section 목록이 있으면 fallback/timeout flag가 false여도 partial로 보고 멈춘다")
    void warmupTodayBootstrapStopsWhenSectionListsArePresentEvenIfFlagsAreFalse() {
        LocalDate today = LocalDate.of(2026, 5, 15);
        HomeBootstrapWarmupService service =
                new HomeBootstrapWarmupService(homePageFacadeService, FIXED_CLOCK, true, Duration.ZERO);
        when(homePageFacadeService.refreshBootstrap(today, WARMUP_SECTION_TIMEOUT))
                .thenReturn(partialBootstrapResponseWithFalseFlags());

        service.warmupTodayBootstrap();

        verify(homePageFacadeService).refreshBootstrap(today, WARMUP_SECTION_TIMEOUT);
        verifyNoMoreInteractions(homePageFacadeService);
    }

    @Test
    @DisplayName("warm-up은 manual-data metadata가 있으면 section 실패가 없어도 partial로 보고 멈춘다")
    void warmupTodayBootstrapStopsWhenManualDataRequestExistsWithoutFailedSections() {
        LocalDate today = LocalDate.of(2026, 5, 15);
        HomeBootstrapWarmupService service =
                new HomeBootstrapWarmupService(homePageFacadeService, FIXED_CLOCK, true, Duration.ZERO);
        when(homePageFacadeService.refreshBootstrap(today, WARMUP_SECTION_TIMEOUT))
                .thenReturn(partialBootstrapResponseWithManualDataOnly());

        service.warmupTodayBootstrap();

        verify(homePageFacadeService).refreshBootstrap(today, WARMUP_SECTION_TIMEOUT);
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
    @DisplayName("warm-up max-attempts 설정과 무관하게 partial 응답은 ranking warm-up까지 진행하지 않는다")
    void warmupTodayBootstrapDoesNotWarmRankingAfterPartialResponse() {
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

    private HomeBootstrapResponseDto partialBootstrapResponseWithManualDataOnly() {
        return HomeBootstrapResponseDto.builder()
                .selectedDate("2026-05-15")
                .loadState(HomeBootstrapLoadStateDto.builder()
                        .isFallback(false)
                        .timedOut(false)
                        .timedOutSections(List.of())
                        .failedSections(List.of())
                        .failureReason("manual-data-required")
                        .manualDataRequest(new ManualBaseballDataRequest(
                                "home.schedule",
                                List.of(new ManualBaseballDataMissingItem(
                                        "final_score",
                                        "최종 점수",
                                        "과거 경기의 최종 점수가 비어 있습니다.",
                                        "home_score, away_score")),
                                "다음 야구 데이터가 필요합니다: 경기 ID=20260515LGKT0",
                                true))
                        .build())
                .build();
    }
}
