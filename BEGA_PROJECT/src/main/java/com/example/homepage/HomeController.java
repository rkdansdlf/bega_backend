package com.example.homepage;

import com.example.kbo.validation.ManualBaseballDataRequiredException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/home")
public class HomeController {

    private static final List<String> HOME_BOOTSTRAP_CORE_SECTIONS = List.of(
            "leagueStartDates",
            "navigation",
            "games",
            "scheduledGamesWindow");

    private final HomePageFacadeService homePageFacadeService;
    private final HomePageGameService homePageGameService;
    private final MeterRegistry meterRegistry;

    public HomeController(HomePageFacadeService homePageFacadeService, HomePageGameService homePageGameService) {
        this(homePageFacadeService, homePageGameService, Metrics.globalRegistry);
    }

    @Autowired
    public HomeController(
            HomePageFacadeService homePageFacadeService,
            HomePageGameService homePageGameService,
            MeterRegistry meterRegistry) {
        this.homePageFacadeService = homePageFacadeService;
        this.homePageGameService = homePageGameService;
        this.meterRegistry = meterRegistry == null ? Metrics.globalRegistry : meterRegistry;
    }

    @GetMapping("/bootstrap")
    public ResponseEntity<HomeBootstrapResponseDto> getBootstrap(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate selectedDate = date == null ? LocalDate.now() : date;
        long startedAtNanos = System.nanoTime();
        String result = "success";
        int statusCode = 200;
        try {
            return ResponseEntity.ok(homePageFacadeService.getBootstrap(selectedDate));
        } catch (ManualBaseballDataRequiredException e) {
            result = "manual_data_required";
            statusCode = 409;
            throw e;
        } catch (Exception e) {
            result = "fallback";
            log.warn("Bootstrap failed for date={}, returning empty fallback: {}", selectedDate, e.getMessage());
            return ResponseEntity.ok(buildEmptyBootstrap(selectedDate));
        } finally {
            recordBootstrapRequestDuration(result, statusCode, System.nanoTime() - startedAtNanos);
        }
    }

    @GetMapping("/widgets")
    public ResponseEntity<HomeWidgetsResponseDto> getWidgets(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer seasonYear) {
        LocalDate selectedDate = date == null ? LocalDate.now() : date;
        try {
            return ResponseEntity.ok(normalizeWidgetsResponse(selectedDate, seasonYear,
                    homePageFacadeService.getWidgets(selectedDate, seasonYear)));
        } catch (Exception e) {
            log.warn("Widgets failed for date={}, returning empty fallback: {}", selectedDate, e.getMessage());
            return ResponseEntity.ok(normalizeWidgetsResponse(selectedDate, seasonYear, null));
        }
    }

    @GetMapping("/navigation")
    public ResponseEntity<HomeScopedNavigationDto> getNavigation(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "regular") String scope,
            @RequestParam(required = false) Integer seasonYear) {
        LocalDate selectedDate = date == null ? LocalDate.now() : date;
        try {
            return ResponseEntity.ok(homePageGameService.getScopedNavigation(selectedDate, scope, seasonYear));
        } catch (ManualBaseballDataRequiredException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Scoped home navigation failed for date={}, scope={}, seasonYear={}: {}",
                    selectedDate,
                    scope,
                    seasonYear,
                    e.getMessage());
            return ResponseEntity.ok(HomeScopedNavigationDto.builder()
                    .resolvedDate(null)
                    .prevGameDate(null)
                    .nextGameDate(null)
                    .hasPrev(false)
                    .hasNext(false)
                    .build());
        }
    }

    private HomeWidgetsResponseDto normalizeWidgetsResponse(
            LocalDate selectedDate,
            Integer seasonYear,
            HomeWidgetsResponseDto response) {
        HomeRankingSnapshotDto rankingSnapshot = response != null ? response.getRankingSnapshot() : null;
        boolean appliedFallbackSnapshot = !isValidRankingSnapshot(rankingSnapshot);
        if (appliedFallbackSnapshot) {
            log.warn(
                    "event=home_widgets_contract_normalized date={} seasonYear={} reason=missing_or_incomplete_ranking_snapshot",
                    selectedDate,
                    seasonYear);
        }

        return HomeWidgetsResponseDto.builder()
                .hotCheerPosts(response != null && response.getHotCheerPosts() != null ? response.getHotCheerPosts() : List.of())
                .featuredMates(response != null && response.getFeaturedMates() != null ? response.getFeaturedMates() : List.of())
                .rankingSnapshot(appliedFallbackSnapshot
                        ? buildFallbackRankingSnapshot(selectedDate, seasonYear)
                        : rankingSnapshot)
                .build();
    }

    private HomeBootstrapResponseDto buildEmptyBootstrap(LocalDate selectedDate) {
        return HomeBootstrapResponseDto.builder()
                .selectedDate(selectedDate.toString())
                .leagueStartDates(LeagueStartDatesDto.builder()
                        .regularSeasonStart(selectedDate.toString())
                        .postseasonStart(null)
                        .koreanSeriesStart(null)
                        .build())
                .navigation(HomeScheduleNavigationDto.builder()
                        .hasPrev(false)
                        .hasNext(false)
                        .build())
                .games(List.of())
                .scheduledGamesWindow(List.of())
                .loadState(HomeBootstrapLoadStateDto.builder()
                        .isFallback(true)
                        .timedOut(false)
                        .timedOutSections(List.of())
                        .failedSections(HOME_BOOTSTRAP_CORE_SECTIONS)
                        .failureReason("request-failed")
                        .build())
                .build();
    }

    private HomeRankingSnapshotDto buildFallbackRankingSnapshot(LocalDate selectedDate, Integer seasonYear) {
        boolean offSeason = seasonYear == null && isAutomaticOffSeason(selectedDate);
        int rankingSeasonYear = seasonYear == null
                ? (offSeason ? selectedDate.getYear() - 1 : selectedDate.getYear())
                : seasonYear;
        return HomeRankingSnapshotDto.builder()
                .rankingSeasonYear(rankingSeasonYear)
                .rankingSourceMessage("순위 데이터를 불러오지 못했습니다.")
                .isOffSeason(offSeason)
                .rankings(List.of())
                .build();
    }

    private boolean isValidRankingSnapshot(HomeRankingSnapshotDto rankingSnapshot) {
        return rankingSnapshot != null
                && rankingSnapshot.getRankingSeasonYear() != null
                && rankingSnapshot.getRankingSourceMessage() != null
                && !rankingSnapshot.getRankingSourceMessage().isBlank()
                && rankingSnapshot.getRankings() != null;
    }

    private boolean isAutomaticOffSeason(LocalDate selectedDate) {
        int month = selectedDate.getMonthValue();
        int day = selectedDate.getDayOfMonth();
        return month >= 11 || month <= 2 || (month == 3 && day < 22);
    }

    private void recordBootstrapRequestDuration(String result, int statusCode, long durationNanos) {
        if (durationNanos < 0) {
            return;
        }

        Timer.builder("home_bootstrap_request_duration_seconds")
                .description("Home bootstrap request duration")
                .publishPercentileHistogram()
                .tags(
                        "result", normalizeMetricTag(result),
                        "status_group", normalizeStatusGroup(statusCode))
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private String normalizeMetricTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase();
    }

    private String normalizeStatusGroup(int statusCode) {
        if (statusCode < 100) {
            return "unknown";
        }
        return (statusCode / 100) + "xx";
    }
}
