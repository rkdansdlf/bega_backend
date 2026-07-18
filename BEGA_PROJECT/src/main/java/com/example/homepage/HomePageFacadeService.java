package com.example.homepage;

import com.example.cheerboard.dto.PostSummaryRes;
import com.example.cheerboard.service.CheerService;
import com.example.kbo.validation.ManualBaseballDataOverrideService;
import com.example.kbo.validation.ManualBaseballDataRequest;
import com.example.kbo.validation.ManualBaseballDataRequiredException;
import com.example.homepage.port.FeaturedMateQuery;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class HomePageFacadeService {

    private static final Duration DEFAULT_SECTION_TIMEOUT = Duration.ofMillis(2500);
    private static final Duration DEFAULT_WIDGETS_SECTION_TIMEOUT = Duration.ofMillis(1200);
    private static final int DEFAULT_SECTION_MAX_CONCURRENCY = 32;
    private static final Duration MANUAL_DATA_NEGATIVE_CACHE_TTL = Duration.ofSeconds(60);
    private static final String SECTION_TIMEOUT_PROPERTY = "${app.home.bootstrap.section-timeout-ms:2500}";
    private static final String WIDGETS_SECTION_TIMEOUT_PROPERTY = "${app.home.widgets.section-timeout-ms:1200}";
    private static final String RANKING_FALLBACK_SOURCE_MESSAGE = "순위 데이터를 불러오지 못했습니다.";
    private static final String SECTION_LEAGUE_START_DATES = "leagueStartDates";
    private static final String SECTION_NAVIGATION = "navigation";
    private static final String SECTION_GAMES = "games";
    private static final String SECTION_SCHEDULED_GAMES_WINDOW = "scheduledGamesWindow";
    private static final String SECTION_HOT_CHEER_POSTS = "hotCheerPosts";
    private static final String SECTION_FEATURED_MATES = "featuredMates";
    private static final String SECTION_RANKING_SNAPSHOT = "rankingSnapshot";
    private static final String FAILURE_REASON_MANUAL_DATA_REQUIRED = "manual-data-required";
    private static final String FAILURE_REASON_REQUEST_FAILED = "request-failed";

    private final HomePageGameService homePageGameService;
    private final CheerService cheerService;
    private final FeaturedMateQuery partyService;
    private final HomeBootstrapCacheService homeBootstrapCacheService;
    private final HomeRankingSnapshotCacheService homeRankingSnapshotCacheService;
    private final HomeWidgetsCacheService homeWidgetsCacheService;
    private final Duration sectionTimeout;
    private final Duration widgetsSectionTimeout;
    private final Clock clock;
    private final ExecutorService sectionExecutor;
    private final MeterRegistry meterRegistry;
    private final ManualBaseballDataOverrideService manualBaseballDataOverrideService;
    private final ConcurrentMap<String, CachedManualDataFailure> manualDataFailureCache = new ConcurrentHashMap<>();
    private volatile Semaphore sectionBulkhead = new Semaphore(DEFAULT_SECTION_MAX_CONCURRENCY);
    private volatile int sectionBulkheadPermits = DEFAULT_SECTION_MAX_CONCURRENCY;

    @Value("${app.home.sections.max-concurrency:32}")
    private int sectionMaxConcurrency = DEFAULT_SECTION_MAX_CONCURRENCY;

    public HomePageFacadeService(
            HomePageGameService homePageGameService,
            CheerService cheerService,
            FeaturedMateQuery partyService) {
        this(
                homePageGameService,
                cheerService,
                partyService,
                null,
                null,
                DEFAULT_SECTION_TIMEOUT,
                DEFAULT_WIDGETS_SECTION_TIMEOUT,
                Clock.systemDefaultZone(),
                Executors.newVirtualThreadPerTaskExecutor(),
                Metrics.globalRegistry);
    }

    @Autowired
    public HomePageFacadeService(
            HomePageGameService homePageGameService,
            CheerService cheerService,
            FeaturedMateQuery partyService,
            HomeBootstrapCacheService homeBootstrapCacheService,
            HomeRankingSnapshotCacheService homeRankingSnapshotCacheService,
            HomeWidgetsCacheService homeWidgetsCacheService,
            @Value(SECTION_TIMEOUT_PROPERTY) long sectionTimeoutMs,
            @Value(WIDGETS_SECTION_TIMEOUT_PROPERTY) long widgetsSectionTimeoutMs,
            MeterRegistry meterRegistry,
            ManualBaseballDataOverrideService manualBaseballDataOverrideService) {
        this(
                homePageGameService,
                cheerService,
                partyService,
                homeBootstrapCacheService,
                homeRankingSnapshotCacheService,
                homeWidgetsCacheService,
                Duration.ofMillis(sectionTimeoutMs),
                Duration.ofMillis(widgetsSectionTimeoutMs),
                Clock.systemDefaultZone(),
                Executors.newVirtualThreadPerTaskExecutor(),
                meterRegistry,
                manualBaseballDataOverrideService);
    }

    HomePageFacadeService(
            HomePageGameService homePageGameService,
            CheerService cheerService,
            FeaturedMateQuery partyService,
            Duration sectionTimeout) {
        this(
                homePageGameService,
                cheerService,
                partyService,
                null,
                null,
                sectionTimeout,
                sectionTimeout,
                Clock.systemDefaultZone(),
                Executors.newVirtualThreadPerTaskExecutor(),
                Metrics.globalRegistry);
    }

    HomePageFacadeService(
            HomePageGameService homePageGameService,
            CheerService cheerService,
            FeaturedMateQuery partyService,
            Duration sectionTimeout,
            Clock clock) {
        this(
                homePageGameService,
                cheerService,
                partyService,
                null,
                null,
                sectionTimeout,
                sectionTimeout,
                clock,
                Executors.newVirtualThreadPerTaskExecutor(),
                Metrics.globalRegistry);
    }

    HomePageFacadeService(
            HomePageGameService homePageGameService,
            CheerService cheerService,
            FeaturedMateQuery partyService,
            Duration sectionTimeout,
            Clock clock,
            ExecutorService sectionExecutor) {
        this(
                homePageGameService,
                cheerService,
                partyService,
                null,
                null,
                sectionTimeout,
                sectionTimeout,
                clock,
                sectionExecutor,
                Metrics.globalRegistry);
    }

    HomePageFacadeService(
            HomePageGameService homePageGameService,
            CheerService cheerService,
            FeaturedMateQuery partyService,
            HomeBootstrapCacheService homeBootstrapCacheService,
            HomeRankingSnapshotCacheService homeRankingSnapshotCacheService,
            Duration sectionTimeout,
            Duration widgetsSectionTimeout,
            Clock clock,
            ExecutorService sectionExecutor,
            MeterRegistry meterRegistry) {
        this(
                homePageGameService,
                cheerService,
                partyService,
                homeBootstrapCacheService,
                homeRankingSnapshotCacheService,
                sectionTimeout,
                widgetsSectionTimeout,
                clock,
                sectionExecutor,
                meterRegistry,
                ManualBaseballDataOverrideService.disabled());
    }

    HomePageFacadeService(
            HomePageGameService homePageGameService,
            CheerService cheerService,
            FeaturedMateQuery partyService,
            HomeBootstrapCacheService homeBootstrapCacheService,
            HomeRankingSnapshotCacheService homeRankingSnapshotCacheService,
            Duration sectionTimeout,
            Duration widgetsSectionTimeout,
            Clock clock,
            ExecutorService sectionExecutor,
            MeterRegistry meterRegistry,
            ManualBaseballDataOverrideService manualBaseballDataOverrideService) {
        this(
                homePageGameService,
                cheerService,
                partyService,
                homeBootstrapCacheService,
                homeRankingSnapshotCacheService,
                null,
                sectionTimeout,
                widgetsSectionTimeout,
                clock,
                sectionExecutor,
                meterRegistry,
                manualBaseballDataOverrideService);
    }

    HomePageFacadeService(
            HomePageGameService homePageGameService,
            CheerService cheerService,
            FeaturedMateQuery partyService,
            HomeBootstrapCacheService homeBootstrapCacheService,
            HomeRankingSnapshotCacheService homeRankingSnapshotCacheService,
            HomeWidgetsCacheService homeWidgetsCacheService,
            Duration sectionTimeout,
            Duration widgetsSectionTimeout,
            Clock clock,
            ExecutorService sectionExecutor,
            MeterRegistry meterRegistry,
            ManualBaseballDataOverrideService manualBaseballDataOverrideService) {
        this.homePageGameService = homePageGameService;
        this.cheerService = cheerService;
        this.partyService = partyService;
        this.homeBootstrapCacheService = homeBootstrapCacheService;
        this.homeRankingSnapshotCacheService = homeRankingSnapshotCacheService;
        this.homeWidgetsCacheService = homeWidgetsCacheService;
        this.sectionTimeout = (sectionTimeout == null || sectionTimeout.isZero() || sectionTimeout.isNegative())
                ? DEFAULT_SECTION_TIMEOUT
                : sectionTimeout;
        this.widgetsSectionTimeout = (widgetsSectionTimeout == null
                || widgetsSectionTimeout.isZero()
                || widgetsSectionTimeout.isNegative())
                ? DEFAULT_WIDGETS_SECTION_TIMEOUT
                : widgetsSectionTimeout;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.sectionExecutor = sectionExecutor == null
                ? Executors.newVirtualThreadPerTaskExecutor()
                : sectionExecutor;
        this.meterRegistry = meterRegistry == null ? Metrics.globalRegistry : meterRegistry;
        this.manualBaseballDataOverrideService = manualBaseballDataOverrideService == null
                ? ManualBaseballDataOverrideService.disabled()
                : manualBaseballDataOverrideService;
        registerSectionBulkheadMetrics();
    }

    public HomeBootstrapResponseDto getBootstrap(LocalDate date) {
        LocalDate selectedDate = date == null ? LocalDate.now(clock) : date;
        if (homeBootstrapCacheService == null) {
            return loadBootstrapUncached(selectedDate);
        }
        return homeBootstrapCacheService.getOrLoad(selectedDate, () -> loadBootstrapUncached(selectedDate));
    }

    HomeBootstrapResponseDto refreshBootstrap(LocalDate date) {
        return refreshBootstrap(date, sectionTimeout);
    }

    HomeBootstrapResponseDto refreshBootstrap(LocalDate date, Duration overrideSectionTimeout) {
        LocalDate selectedDate = date == null ? LocalDate.now(clock) : date;
        Duration effectiveSectionTimeout = normalizeSectionTimeout(overrideSectionTimeout);
        if (homeBootstrapCacheService == null) {
            return loadBootstrapUncached(selectedDate, effectiveSectionTimeout);
        }
        return homeBootstrapCacheService.refresh(selectedDate, () -> loadBootstrapUncached(selectedDate, effectiveSectionTimeout));
    }

    HomeBootstrapResponseDto loadBootstrapUncached(LocalDate date) {
        return loadBootstrapUncached(date, sectionTimeout);
    }

    HomeBootstrapResponseDto loadBootstrapUncached(LocalDate date, Duration overrideSectionTimeout) {
        LocalDate selectedDate = date == null ? LocalDate.now(clock) : date;
        Duration effectiveSectionTimeout = normalizeSectionTimeout(overrideSectionTimeout);
        long bootstrapStartedAt = System.nanoTime();
        if (manualBaseballDataOverrideService.isDateRequiresManualData(selectedDate)) {
            return buildManualDataRequiredBootstrapFallback(
                    selectedDate,
                    bootstrapStartedAt,
                    effectiveSectionTimeout,
                    manualBaseballDataOverrideService.buildRequest("home.schedule", selectedDate));
        }

        SectionTask<LeagueStartDatesDto> leagueStartDatesTask = submitSection(
                sectionExecutor,
                SECTION_LEAGUE_START_DATES,
                () -> loadManualDataGuardedSection(
                        selectedDate,
                        SECTION_LEAGUE_START_DATES,
                        homePageGameService::getLeagueStartDates));
        SectionTask<HomeScheduleNavigationDto> navigationTask = submitSection(
                sectionExecutor,
                SECTION_NAVIGATION,
                () -> loadManualDataGuardedSection(
                        selectedDate,
                        SECTION_NAVIGATION,
                        () -> toHomeScheduleNavigation(homePageGameService.getScheduleNavigation(selectedDate))));
        SectionTask<HomePageGamesResult> gamesTask =
                submitSection(
                        sectionExecutor,
                        SECTION_GAMES,
                        () -> loadManualDataGuardedSection(
                                selectedDate,
                                SECTION_GAMES,
                                () -> homePageGameService.getGamesByDateAllowingPartialManualData(selectedDate)));
        SectionTask<List<HomePageScheduledGameDto>> scheduledGamesTask =
                submitSection(
                        sectionExecutor,
                        SECTION_SCHEDULED_GAMES_WINDOW,
                        () -> loadManualDataGuardedSection(
                                selectedDate,
                                SECTION_SCHEDULED_GAMES_WINDOW,
                                () -> getScheduledGamesWindowForBootstrap(selectedDate)));

        SectionResult<HomeScheduleNavigationDto> navigationResult =
                awaitSection(selectedDate, navigationTask, buildFallbackNavigation(), effectiveSectionTimeout);
        SectionResult<HomePageGamesResult> gamesResult =
                awaitSection(selectedDate, gamesTask, HomePageGamesResult.empty(), effectiveSectionTimeout);
        SectionResult<List<HomePageScheduledGameDto>> scheduledGamesResult =
                awaitSection(selectedDate, scheduledGamesTask, List.of(), effectiveSectionTimeout);
        SectionResult<LeagueStartDatesDto> leagueStartDatesResult =
                awaitSection(selectedDate, leagueStartDatesTask, buildFallbackLeagueStartDates(selectedDate), effectiveSectionTimeout);
        HomeBootstrapLoadStateDto loadState = buildBootstrapLoadState(List.of(
                leagueStartDatesResult,
                navigationResult,
                gamesResult,
                scheduledGamesResult));

        HomeBootstrapResponseDto response = HomeBootstrapResponseDto.builder()
                .selectedDate(selectedDate.toString())
                .leagueStartDates(leagueStartDatesResult.value())
                .navigation(navigationResult.value())
                .games(gamesResult.value().games())
                .scheduledGamesWindow(scheduledGamesResult.value())
                .loadState(loadState)
                .build();

        log.info(
                "event=home_bootstrap_completed date={} totalElapsedMs={} sectionTimeoutMs={} timedOutSections={}",
                selectedDate,
                elapsedMillis(bootstrapStartedAt),
                effectiveSectionTimeout.toMillis(),
                loadState.getTimedOutSections().size());

        return response;
    }

    private HomeBootstrapResponseDto buildManualDataRequiredBootstrapFallback(
            LocalDate selectedDate,
            long bootstrapStartedAt,
            Duration effectiveSectionTimeout,
            ManualBaseballDataRequest manualDataRequest) {
        HomeBootstrapLoadStateDto loadState = HomeBootstrapLoadStateDto.builder()
                .isFallback(true)
                .timedOut(false)
                .timedOutSections(List.of())
                .failedSections(List.of(
                        SECTION_NAVIGATION,
                        SECTION_GAMES,
                        SECTION_SCHEDULED_GAMES_WINDOW))
                .failureReason(FAILURE_REASON_MANUAL_DATA_REQUIRED)
                .manualDataRequest(manualDataRequest)
                .build();
        log.warn(
                "event=home_bootstrap_manual_data_required_fast_fallback date={} totalElapsedMs={} sectionTimeoutMs={} failedSections={}",
                selectedDate,
                elapsedMillis(bootstrapStartedAt),
                effectiveSectionTimeout.toMillis(),
                loadState.getFailedSections());
        return HomeBootstrapResponseDto.builder()
                .selectedDate(selectedDate.toString())
                .leagueStartDates(buildFallbackLeagueStartDates(selectedDate))
                .navigation(buildFallbackNavigation())
                .games(List.of())
                .scheduledGamesWindow(List.of())
                .loadState(loadState)
                .build();
    }

    public String buildBootstrapCacheKey(LocalDate date) {
        if (homeBootstrapCacheService != null) {
            return homeBootstrapCacheService.buildCacheKey(date);
        }
        LocalDate selectedDate = date == null ? LocalDate.now(clock) : date;
        LocalDate today = LocalDate.now(clock);
        return selectedDate + ":today:" + today;
    }

    @PreDestroy
    void shutdownSectionExecutor() {
        sectionExecutor.shutdownNow();
    }

    @Transactional(readOnly = true)
    public HomeWidgetsResponseDto getWidgets(LocalDate date, Integer seasonYear) {
        LocalDate selectedDate = date == null ? LocalDate.now(clock) : date;
        if (homeWidgetsCacheService != null) {
            return homeWidgetsCacheService.getOrLoad(
                    selectedDate,
                    seasonYear,
                    () -> loadWidgetsUncached(selectedDate, seasonYear),
                    this::isUncacheableWidgetsResponse);
        }
        return loadWidgetsUncached(selectedDate, seasonYear);
    }

    HomeWidgetsResponseDto loadWidgetsUncached(LocalDate date, Integer seasonYear) {
        LocalDate selectedDate = date == null ? LocalDate.now(clock) : date;
        SectionTask<List<PostSummaryRes>> hotPostsTask =
                submitSection(sectionExecutor, SECTION_HOT_CHEER_POSTS, this::loadHotPosts);
        SectionTask<List<FeaturedMateCardDto>> featuredMatesTask =
                submitSection(sectionExecutor, SECTION_FEATURED_MATES, () -> loadFeaturedMates(selectedDate));
        SectionTask<HomeRankingSnapshotDto> rankingSnapshotTask =
                submitSection(sectionExecutor, SECTION_RANKING_SNAPSHOT, () -> getRankingSnapshot(selectedDate, seasonYear));

        List<PostSummaryRes> hotPosts = awaitWidgetSection(
                selectedDate,
                seasonYear,
                hotPostsTask,
                List::of,
                ignored -> "success");
        List<FeaturedMateCardDto> featuredMates = awaitWidgetSection(
                selectedDate,
                seasonYear,
                featuredMatesTask,
                List::of,
                ignored -> "success");
        HomeRankingSnapshotDto rankingSnapshot = awaitWidgetSection(
                selectedDate,
                seasonYear,
                rankingSnapshotTask,
                () -> buildFastFallbackRankingSnapshot(selectedDate, seasonYear),
                ranking -> isFallbackRankingSnapshot(ranking) ? "fallback" : "success");

        return HomeWidgetsResponseDto.builder()
                .hotCheerPosts(hotPosts)
                .featuredMates(featuredMates)
                .rankingSnapshot(rankingSnapshot)
                .build();
    }

    String buildWidgetsCacheKey(LocalDate date, Integer seasonYear) {
        if (homeWidgetsCacheService != null) {
            return homeWidgetsCacheService.buildCacheKey(date, seasonYear);
        }
        LocalDate selectedDate = date == null ? LocalDate.now(clock) : date;
        return selectedDate + ":" + (seasonYear == null ? "auto" : seasonYear.toString());
    }

    private List<PostSummaryRes> loadHotPosts() {
        try {
            return cheerService
                    .getHotPostsPublic(PageRequest.of(0, 3), "HYBRID")
                    .getContent();
        } catch (Exception e) {
            log.warn("Failed to load hot cheer posts: {}", e.getMessage());
            return List.of();
        }
    }

    private List<FeaturedMateCardDto> loadFeaturedMates(LocalDate date) {
        try {
            return partyService.getFeaturedMateCards(date, 4);
        } catch (Exception e) {
            log.warn("Failed to load featured mates: {}", e.getMessage());
            return List.of();
        }
    }

    public boolean isUncacheableWidgetsResponse(HomeWidgetsResponseDto response) {
        if (response == null || response.getRankingSnapshot() == null) {
            return true;
        }
        return isFallbackRankingSnapshot(response.getRankingSnapshot());
    }

    @Transactional(readOnly = true)
    public HomeRankingSnapshotDto getRankingSnapshot(LocalDate date, Integer seasonYear) {
        LocalDate selectedDate = date == null ? LocalDate.now(clock) : date;
        if (homeRankingSnapshotCacheService != null) {
            return homeRankingSnapshotCacheService.getOrLoad(
                    selectedDate,
                    seasonYear,
                    () -> loadRankingSnapshotUncached(selectedDate, seasonYear));
        }
        return loadRankingSnapshotUncached(selectedDate, seasonYear);
    }

    HomeRankingSnapshotDto loadRankingSnapshotUncached(LocalDate date, Integer seasonYear) {
        try {
            LeagueStartDatesDto startDates = seasonYear == null ? safeGetLeagueStartDates(date) : null;
            return seasonYear == null
                    ? safeResolveRankingSnapshot(date, startDates)
                    : safeResolveExactSeasonRankingSnapshot(seasonYear);
        } catch (Exception e) {
            log.warn("Failed to load ranking snapshot for date={}, seasonYear={}: {}", date, seasonYear, e.getMessage());
            return seasonYear == null
                    ? buildFallbackRankingSnapshot(date, safeGetLeagueStartDates(date))
                    : buildExplicitSeasonFallbackRankingSnapshot(seasonYear);
        }
    }

    private <T> SectionTask<T> submitSection(ExecutorService executor, String name, Supplier<T> supplier) {
        long startedAtNanos = System.nanoTime();
        return new SectionTask<>(name, executor.submit(() -> runWithSectionPermit(supplier)), startedAtNanos);
    }

    private <T> T runWithSectionPermit(Supplier<T> supplier) {
        Semaphore bulkhead = currentSectionBulkhead();
        boolean acquired = false;
        try {
            bulkhead.acquire();
            acquired = true;
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for home section permit", e);
        } finally {
            if (acquired) {
                bulkhead.release();
            }
        }
    }

    private Semaphore currentSectionBulkhead() {
        int permits = normalizedSectionMaxConcurrency();
        Semaphore localBulkhead = sectionBulkhead;
        if (sectionBulkheadPermits == permits) {
            return localBulkhead;
        }

        synchronized (this) {
            if (sectionBulkheadPermits != permits) {
                sectionBulkhead = new Semaphore(permits);
                sectionBulkheadPermits = permits;
            }
            return sectionBulkhead;
        }
    }

    private int normalizedSectionMaxConcurrency() {
        return Math.max(1, sectionMaxConcurrency);
    }

    private void registerSectionBulkheadMetrics() {
        Gauge.builder(
                        "home_section_bulkhead_active",
                        this,
                        HomePageFacadeService::activeSectionBulkheadPermits)
                .description("Active home bootstrap/widgets section bulkhead permits")
                .tag("bulkhead", "home_sections")
                .register(meterRegistry);
        Gauge.builder(
                        "home_section_bulkhead_limit",
                        this,
                        HomePageFacadeService::sectionBulkheadLimit)
                .description("Configured home bootstrap/widgets section bulkhead permits")
                .tag("bulkhead", "home_sections")
                .register(meterRegistry);
    }

    private double activeSectionBulkheadPermits() {
        Semaphore bulkhead = currentSectionBulkhead();
        return Math.max(0, normalizedSectionMaxConcurrency() - bulkhead.availablePermits());
    }

    private double sectionBulkheadLimit() {
        return normalizedSectionMaxConcurrency();
    }

    private <T> T loadManualDataGuardedSection(LocalDate date, String section, Supplier<T> supplier) {
        String cacheKey = manualDataCacheKey(date, section);
        throwCachedManualDataRequiredSection(cacheKey);
        try {
            return supplier.get();
        } catch (ManualBaseballDataRequiredException e) {
            cacheManualDataRequiredSection(cacheKey, e);
            throw e;
        }
    }

    private <T> SectionResult<T> awaitSection(
            LocalDate date,
            SectionTask<T> task,
            T fallbackValue,
            Duration sectionTimeout) {
        long elapsedBeforeWaitNanos = elapsedNanos(task.startedAtNanos());
        long remainingTimeoutNanos = sectionTimeout.toNanos() - elapsedBeforeWaitNanos;
        if (remainingTimeoutNanos <= 0) {
            if (task.future().isDone()) {
                return getCompletedBootstrapSection(date, task, fallbackValue, sectionTimeout);
            }
            task.future().cancel(true);
            recordSectionDuration(task.name(), "timeout", elapsedNanos(task.startedAtNanos()));
            log.warn(
                    "event=home_bootstrap_section_timed_out date={} section={} elapsedMs={} timeoutMs={}",
                    date,
                    task.name(),
                    TimeUnit.NANOSECONDS.toMillis(elapsedBeforeWaitNanos),
                    sectionTimeout.toMillis());
            return new SectionResult<>(
                    task.name(),
                    fallbackValue,
                    true,
                    true,
                    TimeUnit.NANOSECONDS.toMillis(elapsedBeforeWaitNanos),
                    null);
        }

        try {
            T value = task.future().get(remainingTimeoutNanos, TimeUnit.NANOSECONDS);
            long elapsedMs = elapsedMillis(task.startedAtNanos());
            recordSectionDuration(task.name(), "success", elapsedNanos(task.startedAtNanos()));
            log.info(
                    "event=home_bootstrap_section_completed date={} section={} elapsedMs={} timeoutMs={}",
                    date,
                    task.name(),
                    elapsedMs,
                    sectionTimeout.toMillis());
            return new SectionResult<>(task.name(), value, false, false, elapsedMs, null);
        } catch (TimeoutException ex) {
            if (task.future().isDone() && !task.future().isCancelled()) {
                return getCompletedBootstrapSection(date, task, fallbackValue, sectionTimeout);
            }
            long elapsedMs = elapsedMillis(task.startedAtNanos());
            recordSectionDuration(task.name(), "timeout", elapsedNanos(task.startedAtNanos()));
            log.warn(
                    "event=home_bootstrap_section_timed_out date={} section={} elapsedMs={} timeoutMs={}",
                    date,
                    task.name(),
                    elapsedMs,
                    sectionTimeout.toMillis());
            task.future().cancel(true);
            return new SectionResult<>(task.name(), fallbackValue, true, true, elapsedMs, null);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            long elapsedMs = elapsedMillis(task.startedAtNanos());
            recordSectionDuration(task.name(), "interrupted", elapsedNanos(task.startedAtNanos()));
            log.warn(
                    "event=home_bootstrap_section_interrupted date={} section={} elapsedMs={}",
                    date,
                    task.name(),
                    elapsedMs);
            return new SectionResult<>(task.name(), fallbackValue, false, true, elapsedMs, null);
        } catch (ExecutionException ex) {
            long elapsedMs = elapsedMillis(task.startedAtNanos());
            Throwable cause = ex.getCause();
            if (cause instanceof ManualBaseballDataRequiredException manualDataRequiredException) {
                return handleManualDataRequiredBootstrapSection(
                        date,
                        task,
                        fallbackValue,
                        elapsedMs,
                        manualDataRequiredException);
            }
            recordSectionDuration(task.name(), "failed", elapsedNanos(task.startedAtNanos()));
            log.warn(
                    "event=home_bootstrap_section_failed date={} section={} elapsedMs={} cause={}",
                    date,
                    task.name(),
                    elapsedMs,
                    cause == null ? ex.getMessage() : cause.getMessage());
            return new SectionResult<>(task.name(), fallbackValue, false, true, elapsedMs, null);
        }
    }

    private <T> SectionResult<T> getCompletedBootstrapSection(
            LocalDate date,
            SectionTask<T> task,
            T fallbackValue,
            Duration sectionTimeout) {
        try {
            T value = task.future().get();
            long elapsedMs = elapsedMillis(task.startedAtNanos());
            recordSectionDuration(task.name(), "success", elapsedNanos(task.startedAtNanos()));
            log.info(
                    "event=home_bootstrap_section_completed date={} section={} elapsedMs={} timeoutMs={}",
                    date,
                    task.name(),
                    elapsedMs,
                    sectionTimeout.toMillis());
            return new SectionResult<>(task.name(), value, false, false, elapsedMs, null);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            long elapsedMs = elapsedMillis(task.startedAtNanos());
            recordSectionDuration(task.name(), "interrupted", elapsedNanos(task.startedAtNanos()));
            log.warn(
                    "event=home_bootstrap_section_interrupted date={} section={} elapsedMs={}",
                    date,
                    task.name(),
                    elapsedMs);
            return new SectionResult<>(task.name(), fallbackValue, false, true, elapsedMs, null);
        } catch (ExecutionException ex) {
            long elapsedMs = elapsedMillis(task.startedAtNanos());
            Throwable cause = ex.getCause();
            if (cause instanceof ManualBaseballDataRequiredException manualDataRequiredException) {
                return handleManualDataRequiredBootstrapSection(
                        date,
                        task,
                        fallbackValue,
                        elapsedMs,
                        manualDataRequiredException);
            }
            recordSectionDuration(task.name(), "failed", elapsedNanos(task.startedAtNanos()));
            log.warn(
                    "event=home_bootstrap_section_failed date={} section={} elapsedMs={} cause={}",
                    date,
                    task.name(),
                    elapsedMs,
                    cause == null ? ex.getMessage() : cause.getMessage());
            return new SectionResult<>(task.name(), fallbackValue, false, true, elapsedMs, null);
        }
    }

    private <T> SectionResult<T> handleManualDataRequiredBootstrapSection(
            LocalDate date,
            SectionTask<T> task,
            T fallbackValue,
            long elapsedMs,
            ManualBaseballDataRequiredException exception) {
        cacheManualDataRequiredSection(manualDataCacheKey(date, task.name()), exception);
        recordSectionDuration(task.name(), "manual_data_required", elapsedNanos(task.startedAtNanos()));
        log.warn(
                "event=home_bootstrap_section_manual_data_required date={} section={} elapsedMs={} code={} message={}",
                date,
                task.name(),
                elapsedMs,
                exception.getCode(),
                exception.getMessage());
        return new SectionResult<>(
                task.name(),
                fallbackValue,
                false,
                true,
                elapsedMs,
                extractManualDataRequest(exception));
    }

    private String manualDataCacheKey(LocalDate date, String section) {
        return section + ":date:" + date;
    }

    private void throwCachedManualDataRequiredSection(String cacheKey) {
        CachedManualDataFailure cached = manualDataFailureCache.get(cacheKey);
        if (cached == null) {
            return;
        }
        long nowMillis = clock.millis();
        if (nowMillis >= cached.expiresAtMillis()) {
            manualDataFailureCache.remove(cacheKey, cached);
            return;
        }
        throw new ManualBaseballDataRequiredException(cached.request());
    }

    private void cacheManualDataRequiredSection(
            String cacheKey,
            ManualBaseballDataRequiredException exception) {
        ManualBaseballDataRequest request = extractManualDataRequest(exception);
        if (request == null) {
            return;
        }
        manualDataFailureCache.put(
                cacheKey,
                new CachedManualDataFailure(
                        request,
                        clock.millis() + MANUAL_DATA_NEGATIVE_CACHE_TTL.toMillis()));
    }

    private ManualBaseballDataRequest extractManualDataRequest(ManualBaseballDataRequiredException exception) {
        Object data = exception.getData();
        if (data instanceof ManualBaseballDataRequest request) {
            return request;
        }
        return null;
    }

    private ManualBaseballDataRequest extractManualDataRequest(SectionResult<?> sectionResult) {
        if (sectionResult.manualDataRequest() != null) {
            return sectionResult.manualDataRequest();
        }
        Object value = sectionResult.value();
        if (value instanceof HomePageGamesResult gamesResult) {
            return gamesResult.manualDataRequest();
        }
        return null;
    }

    private HomeBootstrapLoadStateDto buildBootstrapLoadState(List<SectionResult<?>> sectionResults) {
        List<String> timedOutSections = sectionResults.stream()
                .filter(SectionResult::timedOut)
                .map(SectionResult::section)
                .toList();
        List<String> failedSections = sectionResults.stream()
                .filter(SectionResult::failed)
                .map(SectionResult::section)
                .toList();
        ManualBaseballDataRequest manualDataRequest = sectionResults.stream()
                .map(this::extractManualDataRequest)
                .filter(request -> request != null)
                .findFirst()
                .orElse(null);
        String failureReason = manualDataRequest != null
                ? FAILURE_REASON_MANUAL_DATA_REQUIRED
                : failedSections.isEmpty() ? null : FAILURE_REASON_REQUEST_FAILED;

        return HomeBootstrapLoadStateDto.builder()
                .isFallback(!failedSections.isEmpty())
                .timedOut(!timedOutSections.isEmpty())
                .timedOutSections(timedOutSections)
                .failedSections(failedSections)
                .failureReason(failureReason)
                .manualDataRequest(manualDataRequest)
                .build();
    }

    private LeagueStartDatesDto buildFallbackLeagueStartDates(LocalDate date) {
        return LeagueStartDatesDto.builder()
                .regularSeasonStart(date.toString())
                .postseasonStart(null)
                .koreanSeriesStart(null)
                .build();
    }

    private HomeScheduleNavigationDto buildFallbackNavigation() {
        return HomeScheduleNavigationDto.builder()
                .prevGameDate(null)
                .nextGameDate(null)
                .hasPrev(false)
                .hasNext(false)
                .build();
    }

    private HomeRankingSnapshotDto buildFallbackRankingSnapshot(LocalDate date, LeagueStartDatesDto startDates) {
        boolean offSeason = isOffSeason(date, startDates);
        int rankingSeasonYear = offSeason ? date.getYear() - 1 : date.getYear();
        return HomeRankingSnapshotDto.builder()
                .rankingSeasonYear(rankingSeasonYear)
                .rankingSourceMessage("순위 데이터를 불러오지 못했습니다.")
                .isOffSeason(offSeason)
                .rankings(List.of())
                .build();
    }

    private HomeRankingSnapshotDto buildExplicitSeasonFallbackRankingSnapshot(int seasonYear) {
        return HomeRankingSnapshotDto.builder()
                .rankingSeasonYear(seasonYear)
                .rankingSourceMessage("순위 데이터를 불러오지 못했습니다.")
                .isOffSeason(false)
                .rankings(List.of())
                .build();
    }

    private long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    private Duration normalizeSectionTimeout(Duration timeout) {
        return timeout == null || timeout.isZero() || timeout.isNegative()
                ? DEFAULT_SECTION_TIMEOUT
                : timeout;
    }

    private long elapsedNanos(long startedAtNanos) {
        return System.nanoTime() - startedAtNanos;
    }

    private void recordSectionDuration(String section, String result, long durationNanos) {
        if (durationNanos < 0) {
            return;
        }

        recordSectionEvent("home_bootstrap_section_events_total", "Home bootstrap section events", section, result);
        Timer.builder("home_bootstrap_section_duration_seconds")
                .description("Home bootstrap section duration")
                .publishPercentileHistogram()
                .tags(
                        "section", normalizeMetricTag(section),
                        "result", normalizeMetricTag(result))
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private void recordWidgetsSectionDuration(String section, String result, long durationNanos) {
        if (durationNanos < 0) {
            return;
        }

        recordSectionEvent("home_widgets_section_events_total", "Home widgets section events", section, result);
        Timer.builder("home_widgets_section_duration_seconds")
                .description("Home widgets section duration")
                .publishPercentileHistogram()
                .tags(
                        "section", normalizeMetricTag(section),
                        "result", normalizeMetricTag(result))
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private void recordSectionEvent(String metricName, String description, String section, String result) {
        Counter.builder(metricName)
                .description(description)
                .tags(
                        "section", normalizeMetricTag(section),
                        "result", normalizeMetricTag(result))
                .register(meterRegistry)
                .increment();
    }

    private String normalizeMetricTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase();
    }

    private <T> T awaitWidgetSection(
            LocalDate date,
            Integer seasonYear,
            SectionTask<T> task,
            Supplier<T> fallbackSupplier,
            Function<T, String> resultClassifier) {
        long elapsedBeforeWaitNanos = elapsedNanos(task.startedAtNanos());
        long remainingTimeoutNanos = widgetsSectionTimeout.toNanos() - elapsedBeforeWaitNanos;
        if (remainingTimeoutNanos <= 0) {
            if (task.future().isDone()) {
                return getCompletedWidgetSection(date, seasonYear, task, fallbackSupplier, resultClassifier);
            }
            task.future().cancel(true);
            recordWidgetsSectionDuration(task.name(), "timeout", elapsedNanos(task.startedAtNanos()));
            log.warn(
                    "event=home_widgets_section_timed_out date={} seasonYear={} section={} elapsedMs={} timeoutMs={}",
                    date,
                    seasonYear,
                    task.name(),
                    TimeUnit.NANOSECONDS.toMillis(elapsedBeforeWaitNanos),
                    widgetsSectionTimeout.toMillis());
            return fallbackSupplier.get();
        }

        try {
            T value = task.future().get(remainingTimeoutNanos, TimeUnit.NANOSECONDS);
            recordWidgetsSectionDuration(
                    task.name(),
                    resultClassifier.apply(value),
                    elapsedNanos(task.startedAtNanos()));
            return value;
        } catch (TimeoutException ex) {
            if (task.future().isDone() && !task.future().isCancelled()) {
                return getCompletedWidgetSection(date, seasonYear, task, fallbackSupplier, resultClassifier);
            }
            recordWidgetsSectionDuration(task.name(), "timeout", elapsedNanos(task.startedAtNanos()));
            log.warn(
                    "event=home_widgets_section_timed_out date={} seasonYear={} section={} elapsedMs={} timeoutMs={}",
                    date,
                    seasonYear,
                    task.name(),
                    elapsedMillis(task.startedAtNanos()),
                    widgetsSectionTimeout.toMillis());
            task.future().cancel(true);
            return fallbackSupplier.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            recordWidgetsSectionDuration(task.name(), "interrupted", elapsedNanos(task.startedAtNanos()));
            log.warn(
                    "event=home_widgets_section_interrupted date={} seasonYear={} section={} elapsedMs={}",
                    date,
                    seasonYear,
                    task.name(),
                    elapsedMillis(task.startedAtNanos()));
            return fallbackSupplier.get();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            recordWidgetsSectionDuration(task.name(), "failed", elapsedNanos(task.startedAtNanos()));
            log.warn(
                    "event=home_widgets_section_failed date={} seasonYear={} section={} elapsedMs={} reason={}",
                    date,
                    seasonYear,
                    task.name(),
                    elapsedMillis(task.startedAtNanos()),
                    cause == null ? ex.getMessage() : cause.getMessage());
            return fallbackSupplier.get();
        }
    }

    private <T> T getCompletedWidgetSection(
            LocalDate date,
            Integer seasonYear,
            SectionTask<T> task,
            Supplier<T> fallbackSupplier,
            Function<T, String> resultClassifier) {
        try {
            T value = task.future().get();
            recordWidgetsSectionDuration(
                    task.name(),
                    resultClassifier.apply(value),
                    elapsedNanos(task.startedAtNanos()));
            return value;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            recordWidgetsSectionDuration(task.name(), "interrupted", elapsedNanos(task.startedAtNanos()));
            log.warn(
                    "event=home_widgets_section_interrupted date={} seasonYear={} section={} elapsedMs={}",
                    date,
                    seasonYear,
                    task.name(),
                    elapsedMillis(task.startedAtNanos()));
            return fallbackSupplier.get();
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            recordWidgetsSectionDuration(task.name(), "failed", elapsedNanos(task.startedAtNanos()));
            log.warn(
                    "event=home_widgets_section_failed date={} seasonYear={} section={} elapsedMs={} reason={}",
                    date,
                    seasonYear,
                    task.name(),
                    elapsedMillis(task.startedAtNanos()),
                    cause == null ? ex.getMessage() : cause.getMessage());
            return fallbackSupplier.get();
        }
    }

    private boolean isFallbackRankingSnapshot(HomeRankingSnapshotDto rankingSnapshot) {
        return rankingSnapshot == null
                || RANKING_FALLBACK_SOURCE_MESSAGE.equals(rankingSnapshot.getRankingSourceMessage());
    }

    private HomeRankingSnapshotDto buildFastFallbackRankingSnapshot(LocalDate date, Integer seasonYear) {
        return seasonYear == null
                ? buildFallbackRankingSnapshot(date, (LeagueStartDatesDto) null)
                : buildExplicitSeasonFallbackRankingSnapshot(seasonYear);
    }

    private HomeRankingSnapshotDto buildFallbackRankingSnapshot(LocalDate date, Integer seasonYear) {
        return seasonYear == null
                ? buildFallbackRankingSnapshot(date, safeGetLeagueStartDates(date))
                : buildExplicitSeasonFallbackRankingSnapshot(seasonYear);
    }

    private LeagueStartDatesDto safeGetLeagueStartDates(LocalDate date) {
        try {
            return homePageGameService.getLeagueStartDates();
        } catch (ManualBaseballDataRequiredException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to load league start dates: {}", e.getMessage());
            return buildFallbackLeagueStartDates(date);
        }
    }

    private List<HomePageGameDto> safeGetGames(LocalDate date) {
        try {
            return homePageGameService.getGamesByDate(date);
        } catch (ManualBaseballDataRequiredException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to load games for date={}: {}", date, e.getMessage());
            return List.of();
        }
    }

    private List<HomePageScheduledGameDto> safeGetScheduledGamesWindow(LocalDate date) {
        try {
            return getScheduledGamesWindowForBootstrap(date);
        } catch (ManualBaseballDataRequiredException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to load scheduled games window for date={}: {}", date, e.getMessage());
            return List.of();
        }
    }

    private List<HomePageScheduledGameDto> getScheduledGamesWindowForBootstrap(LocalDate date) {
        LocalDate today = LocalDate.now(clock);
        LocalDate windowStartDate = date.isBefore(today) ? today : date;
        return homePageGameService.getScheduledGamesWindow(windowStartDate, windowStartDate.plusDays(7));
    }

    private HomeScheduleNavigationDto safeGetNavigation(LocalDate date) {
        try {
            return toHomeScheduleNavigation(homePageGameService.getScheduleNavigation(date));
        } catch (ManualBaseballDataRequiredException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to load schedule navigation for date={}: {}", date, e.getMessage());
            return buildFallbackNavigation();
        }
    }

    private HomeRankingSnapshotDto safeResolveRankingSnapshot(LocalDate date, LeagueStartDatesDto startDates) {
        try {
            return resolveRankingSnapshot(date, startDates);
        } catch (Exception e) {
            log.warn("Failed to resolve ranking snapshot for date={}: {}", date, e.getMessage());
            return buildFallbackRankingSnapshot(date, startDates);
        }
    }

    private HomeRankingSnapshotDto safeResolveExactSeasonRankingSnapshot(int seasonYear) {
        try {
            return resolveExactSeasonRankingSnapshot(seasonYear);
        } catch (Exception e) {
            log.warn("Failed to resolve exact season ranking snapshot for seasonYear={}: {}", seasonYear, e.getMessage());
            return buildExplicitSeasonFallbackRankingSnapshot(seasonYear);
        }
    }

    private HomeRankingSnapshotDto resolveRankingSnapshot(LocalDate selectedDate, LeagueStartDatesDto startDates) {
        boolean isOffSeason = isOffSeason(selectedDate, startDates);
        int baseSeasonYear = isOffSeason ? selectedDate.getYear() - 1 : selectedDate.getYear();

        List<HomePageTeamRankingDto> baseRankings = homePageGameService.getTeamRankings(baseSeasonYear);
        if (!baseRankings.isEmpty()) {
            return HomeRankingSnapshotDto.builder()
                    .rankingSeasonYear(baseSeasonYear)
                    .rankingSourceMessage(baseSeasonYear + " 시즌 순위 데이터")
                    .isOffSeason(isOffSeason)
                    .rankings(baseRankings)
                    .build();
        }

        if (!isOffSeason) {
            return HomeRankingSnapshotDto.builder()
                    .rankingSeasonYear(baseSeasonYear)
                    .rankingSourceMessage(baseSeasonYear + " 시즌 데이터가 아직 집계되지 않았습니다.")
                    .isOffSeason(false)
                    .rankings(List.of())
                    .build();
        }

        int fallbackSeasonYear = baseSeasonYear - 1;
        List<HomePageTeamRankingDto> fallbackRankings = homePageGameService.getTeamRankings(fallbackSeasonYear);
        if (!fallbackRankings.isEmpty()) {
            return HomeRankingSnapshotDto.builder()
                    .rankingSeasonYear(fallbackSeasonYear)
                    .rankingSourceMessage(fallbackSeasonYear + " 시즌 순위 데이터")
                    .isOffSeason(true)
                    .rankings(fallbackRankings)
                    .build();
        }

        return HomeRankingSnapshotDto.builder()
                .rankingSeasonYear(baseSeasonYear)
                .rankingSourceMessage("현재 시즌과 전시즌(전년도) 데이터가 없습니다.")
                .isOffSeason(true)
                .rankings(List.of())
                .build();
    }

    private HomeRankingSnapshotDto resolveExactSeasonRankingSnapshot(int seasonYear) {
        List<HomePageTeamRankingDto> rankings = homePageGameService.getTeamRankings(seasonYear);
        if (!rankings.isEmpty()) {
            return HomeRankingSnapshotDto.builder()
                    .rankingSeasonYear(seasonYear)
                    .rankingSourceMessage(seasonYear + " 시즌 순위 데이터")
                    .isOffSeason(false)
                    .rankings(rankings)
                    .build();
        }

        return HomeRankingSnapshotDto.builder()
                .rankingSeasonYear(seasonYear)
                .rankingSourceMessage(seasonYear + " 시즌 데이터가 아직 집계되지 않았습니다.")
                .isOffSeason(false)
                .rankings(List.of())
                .build();
    }

    private boolean isOffSeason(LocalDate targetDate, LeagueStartDatesDto startDates) {
        if (targetDate == null) {
            return false;
        }

        LocalDate normalizedStartDate = parseRegularSeasonStart(targetDate, startDates);
        if (normalizedStartDate == null) {
            int month = targetDate.getMonthValue();
            int day = targetDate.getDayOfMonth();
            return month >= 11 || month <= 2 || (month == 3 && day < 22);
        }

        int month = targetDate.getMonthValue();
        return month >= 11 || month <= 2 || targetDate.isBefore(normalizedStartDate);
    }

    private LocalDate parseRegularSeasonStart(LocalDate targetDate, LeagueStartDatesDto startDates) {
        if (targetDate == null || startDates == null || startDates.getRegularSeasonStart() == null) {
            return null;
        }

        try {
            LocalDate parsed = LocalDate.parse(startDates.getRegularSeasonStart());
            int targetYear = targetDate.getYear();
            int month = parsed.getMonthValue();
            int day = Math.min(parsed.getDayOfMonth(), parsed.getMonth().length(Year.isLeap(targetYear)));
            return LocalDate.of(targetYear, month, day);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private HomeScheduleNavigationDto toHomeScheduleNavigation(ScheduleNavigationDto navigation) {
        if (navigation == null) {
            return buildFallbackNavigation();
        }

        return HomeScheduleNavigationDto.builder()
                .prevGameDate(navigation.getPrevGameDate() == null ? null : navigation.getPrevGameDate().toString())
                .nextGameDate(navigation.getNextGameDate() == null ? null : navigation.getNextGameDate().toString())
                .hasPrev(navigation.isHasPrev())
                .hasNext(navigation.isHasNext())
                .build();
    }

    private record SectionTask<T>(String name, Future<T> future, long startedAtNanos) {
    }

    private record SectionResult<T>(
            String section,
            T value,
            boolean timedOut,
            boolean failed,
            long elapsedMs,
            ManualBaseballDataRequest manualDataRequest) {
    }

    private record CachedManualDataFailure(ManualBaseballDataRequest request, long expiresAtMillis) {
    }
}
