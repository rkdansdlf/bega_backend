package com.example.homepage;

import com.example.kbo.validation.ManualBaseballDataRequiredException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.swagger.v3.oas.annotations.Parameter;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/home")
public class HomeController {

    private static final String WIDGETS_FALLBACK_RANKING_MESSAGE = "순위 데이터를 불러오지 못했습니다.";

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
            @Parameter(description = "1982-01-01부터 2030-12-31까지의 날짜")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate selectedDate = HomeRequestBounds.requireSupportedDate(date);
        long startedAtNanos = System.nanoTime();
        String result = "success";
        int statusCode = 200;
        try {
            return ResponseEntity.ok(homePageFacadeService.getBootstrap(selectedDate));
        } catch (ManualBaseballDataRequiredException e) {
            result = "manual_data_required";
            statusCode = 409;
            throw e;
        } catch (RuntimeException e) {
            result = "failed";
            statusCode = failureStatus(e);
            throw e;
        } finally {
            recordBootstrapRequestDuration(result, statusCode, System.nanoTime() - startedAtNanos);
        }
    }

    @GetMapping("/widgets")
    public ResponseEntity<HomeWidgetsResponseDto> getWidgets(
            @Parameter(description = "1982-01-01부터 2030-12-31까지의 날짜")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "1982년부터 2030년까지의 시즌 연도")
            @RequestParam(required = false) Integer seasonYear) {
        LocalDate selectedDate = HomeRequestBounds.requireSupportedDate(date);
        HomeRequestBounds.requireSupportedSeasonYear(seasonYear);
        long startedAtNanos = System.nanoTime();
        String result = "success";
        int statusCode = 200;
        try {
            HomeWidgetsResponseDto response = normalizeWidgetsResponse(
                    selectedDate,
                    seasonYear,
                    homePageFacadeService.getWidgets(selectedDate, seasonYear));
            if (isFallbackWidgetsResponse(response)) {
                result = "fallback";
            }
            return ResponseEntity.ok(response);
        } catch (ManualBaseballDataRequiredException e) {
            result = "manual_data_required";
            statusCode = 409;
            throw e;
        } catch (RuntimeException e) {
            result = "failed";
            statusCode = failureStatus(e);
            throw e;
        } finally {
            recordWidgetsRequestDuration(result, statusCode, System.nanoTime() - startedAtNanos);
        }
    }

    @GetMapping("/navigation")
    public ResponseEntity<HomeScopedNavigationDto> getNavigation(
            @Parameter(description = "1982-01-01부터 2030-12-31까지의 날짜")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "regular") String scope,
            @Parameter(description = "1982년부터 2030년까지의 시즌 연도")
            @RequestParam(required = false) Integer seasonYear) {
        LocalDate selectedDate = HomeRequestBounds.requireSupportedDate(date);
        HomeRequestBounds.requireSupportedSeasonYear(seasonYear);
        return ResponseEntity.ok(homePageGameService.getScopedNavigation(selectedDate, scope, seasonYear));
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

    private int failureStatus(RuntimeException exception) {
        return exception instanceof TransientDataAccessException
                || exception instanceof CannotGetJdbcConnectionException
                || exception instanceof CannotCreateTransactionException ? 503 : 500;
    }

    private HomeRankingSnapshotDto buildFallbackRankingSnapshot(LocalDate selectedDate, Integer seasonYear) {
        boolean offSeason = seasonYear == null && isAutomaticOffSeason(selectedDate);
        int rankingSeasonYear = seasonYear == null
                ? (offSeason ? selectedDate.getYear() - 1 : selectedDate.getYear())
                : seasonYear;
        return HomeRankingSnapshotDto.builder()
                .rankingSeasonYear(rankingSeasonYear)
                .rankingSourceMessage(WIDGETS_FALLBACK_RANKING_MESSAGE)
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

    private boolean isFallbackWidgetsResponse(HomeWidgetsResponseDto response) {
        return response == null
                || response.getRankingSnapshot() == null
                || WIDGETS_FALLBACK_RANKING_MESSAGE.equals(response.getRankingSnapshot().getRankingSourceMessage());
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

    private void recordWidgetsRequestDuration(String result, int statusCode, long durationNanos) {
        if (durationNanos < 0) {
            return;
        }

        Timer.builder("home_widgets_request_duration_seconds")
                .description("Home widgets request duration")
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
