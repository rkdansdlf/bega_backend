package com.example.homepage;

import com.example.kbo.validation.ManualBaseballDataRequest;
import com.example.kbo.validation.ManualBaseballDataRequiredException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HomeBootstrapWarmupService {

    private static final int MAX_WARMUP_ATTEMPTS = 2;
    private static final Duration DEFAULT_PARTIAL_RETRY_DELAY = Duration.ofMillis(500);
    private static final Duration DEFAULT_WARMUP_SECTION_TIMEOUT = Duration.ofSeconds(8);

    private final HomePageFacadeService homePageFacadeService;
    private final Clock clock;
    private final boolean enabled;
    private final Duration partialRetryDelay;
    private final Duration warmupSectionTimeout;

    @Autowired
    public HomeBootstrapWarmupService(
            HomePageFacadeService homePageFacadeService,
            @Value("${app.home.bootstrap.warmup.enabled:true}") boolean enabled,
            @Value("${app.home.bootstrap.warmup.partial-retry-delay-ms:500}") long partialRetryDelayMs,
            @Value("${app.home.bootstrap.warmup.section-timeout-ms:8000}") long warmupSectionTimeoutMs) {
        this(
                homePageFacadeService,
                Clock.systemDefaultZone(),
                enabled,
                Duration.ofMillis(partialRetryDelayMs),
                Duration.ofMillis(warmupSectionTimeoutMs));
    }

    HomeBootstrapWarmupService(
            HomePageFacadeService homePageFacadeService,
            Clock clock,
            boolean enabled) {
        this(homePageFacadeService, clock, enabled, DEFAULT_PARTIAL_RETRY_DELAY, DEFAULT_WARMUP_SECTION_TIMEOUT);
    }

    HomeBootstrapWarmupService(
            HomePageFacadeService homePageFacadeService,
            Clock clock,
            boolean enabled,
            Duration partialRetryDelay) {
        this(homePageFacadeService, clock, enabled, partialRetryDelay, DEFAULT_WARMUP_SECTION_TIMEOUT);
    }

    HomeBootstrapWarmupService(
            HomePageFacadeService homePageFacadeService,
            Clock clock,
            boolean enabled,
            Duration partialRetryDelay,
            Duration warmupSectionTimeout) {
        this.homePageFacadeService = homePageFacadeService;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.enabled = enabled;
        this.partialRetryDelay = partialRetryDelay == null || partialRetryDelay.isNegative()
                ? Duration.ZERO
                : partialRetryDelay;
        this.warmupSectionTimeout = warmupSectionTimeout == null || warmupSectionTimeout.isZero() || warmupSectionTimeout.isNegative()
                ? DEFAULT_WARMUP_SECTION_TIMEOUT
                : warmupSectionTimeout;
    }

    @Scheduled(fixedDelayString = "${app.home.bootstrap.warmup.fixed-delay-ms:50000}", initialDelay = 5000)
    public void warmupTodayBootstrap() {
        if (!enabled) {
            return;
        }

        LocalDate today = LocalDate.now(clock);
        try {
            HomeBootstrapResponseDto response = null;
            for (int attempt = 1; attempt <= MAX_WARMUP_ATTEMPTS; attempt++) {
                response = homePageFacadeService.refreshBootstrap(today, warmupSectionTimeout);
                if (isComplete(response)) {
                    warmupTodayRankingSnapshot(today);
                    log.info("event=home_bootstrap_warmup_completed date={} attempts={}", today, attempt);
                    return;
                }

                log.warn(
                        "event=home_bootstrap_warmup_partial date={} attempt={} timedOutSections={} failedSections={}",
                        today,
                        attempt,
                        timedOutSections(response),
                        failedSections(response));

                if (attempt < MAX_WARMUP_ATTEMPTS) {
                    sleepBeforeRetry();
                }
            }

            log.warn(
                    "event=home_bootstrap_warmup_completed_partial date={} attempts={} timedOutSections={} failedSections={}",
                    today,
                    MAX_WARMUP_ATTEMPTS,
                    timedOutSections(response),
                    failedSections(response));
            warmupTodayRankingSnapshot(today);
        } catch (ManualBaseballDataRequiredException ex) {
            Object data = ex.getData();
            String scope = data instanceof ManualBaseballDataRequest request ? request.scope() : null;
            log.warn(
                    "event=home_bootstrap_warmup_manual_data_required date={} scope={}",
                    today,
                    scope);
        } catch (Exception ex) {
            log.warn("event=home_bootstrap_warmup_failed date={} reason={}", today, ex.getMessage());
        }
    }

    private void warmupTodayRankingSnapshot(LocalDate today) {
        try {
            HomeRankingSnapshotDto snapshot = homePageFacadeService.getRankingSnapshot(today, null);
            int rankingCount = snapshot == null || snapshot.getRankings() == null ? 0 : snapshot.getRankings().size();
            log.info(
                    "event=home_ranking_snapshot_warmup_completed date={} rankingCount={}",
                    today,
                    rankingCount);
        } catch (ManualBaseballDataRequiredException ex) {
            Object data = ex.getData();
            String scope = data instanceof ManualBaseballDataRequest request ? request.scope() : null;
            log.warn(
                    "event=home_ranking_snapshot_warmup_manual_data_required date={} scope={}",
                    today,
                    scope);
        } catch (Exception ex) {
            log.warn("event=home_ranking_snapshot_warmup_failed date={} reason={}", today, ex.getMessage());
        }
    }

    private boolean isComplete(HomeBootstrapResponseDto response) {
        if (response == null || response.getLoadState() == null) {
            return false;
        }
        HomeBootstrapLoadStateDto loadState = response.getLoadState();
        return !Boolean.TRUE.equals(loadState.getIsFallback())
                && !Boolean.TRUE.equals(loadState.getTimedOut())
                && !hasSections(loadState.getTimedOutSections())
                && !hasSections(loadState.getFailedSections());
    }

    private boolean hasSections(List<String> sections) {
        return sections != null && !sections.isEmpty();
    }

    private Object timedOutSections(HomeBootstrapResponseDto response) {
        if (response == null || response.getLoadState() == null || response.getLoadState().getTimedOutSections() == null) {
            return "[]";
        }
        return response.getLoadState().getTimedOutSections();
    }

    private Object failedSections(HomeBootstrapResponseDto response) {
        if (response == null || response.getLoadState() == null || response.getLoadState().getFailedSections() == null) {
            return "[]";
        }
        return response.getLoadState().getFailedSections();
    }

    private void sleepBeforeRetry() {
        if (partialRetryDelay.isZero()) {
            return;
        }
        try {
            Thread.sleep(partialRetryDelay.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
