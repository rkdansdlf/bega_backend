package com.example.homepage;

import static com.example.common.config.CacheConfig.HOME_WIDGETS;

import com.example.cheerboard.dto.PostSummaryRes;
import com.example.cheerboard.service.CheerService;
import com.example.kbo.validation.ManualBaseballDataRequiredException;
import com.example.mate.service.PartyService;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class HomePageFacadeService {

    private static final Duration DEFAULT_SECTION_TIMEOUT = Duration.ofSeconds(6);
    private static final String SECTION_TIMEOUT_PROPERTY = "${app.home.bootstrap.section-timeout-ms:6000}";

    private final HomePageGameService homePageGameService;
    private final CheerService cheerService;
    private final PartyService partyService;
    private final Duration sectionTimeout;
    private final Clock clock;
    private final ExecutorService sectionExecutor;

    public HomePageFacadeService(
            HomePageGameService homePageGameService,
            CheerService cheerService,
            PartyService partyService) {
        this(
                homePageGameService,
                cheerService,
                partyService,
                DEFAULT_SECTION_TIMEOUT,
                Clock.systemDefaultZone(),
                Executors.newVirtualThreadPerTaskExecutor());
    }

    @Autowired
    public HomePageFacadeService(
            HomePageGameService homePageGameService,
            CheerService cheerService,
            PartyService partyService,
            @Value(SECTION_TIMEOUT_PROPERTY) long sectionTimeoutMs) {
        this(
                homePageGameService,
                cheerService,
                partyService,
                Duration.ofMillis(sectionTimeoutMs),
                Clock.systemDefaultZone(),
                Executors.newVirtualThreadPerTaskExecutor());
    }

    HomePageFacadeService(
            HomePageGameService homePageGameService,
            CheerService cheerService,
            PartyService partyService,
            Duration sectionTimeout) {
        this(
                homePageGameService,
                cheerService,
                partyService,
                sectionTimeout,
                Clock.systemDefaultZone(),
                Executors.newVirtualThreadPerTaskExecutor());
    }

    HomePageFacadeService(
            HomePageGameService homePageGameService,
            CheerService cheerService,
            PartyService partyService,
            Duration sectionTimeout,
            Clock clock) {
        this(
                homePageGameService,
                cheerService,
                partyService,
                sectionTimeout,
                clock,
                Executors.newVirtualThreadPerTaskExecutor());
    }

    HomePageFacadeService(
            HomePageGameService homePageGameService,
            CheerService cheerService,
            PartyService partyService,
            Duration sectionTimeout,
            Clock clock,
            ExecutorService sectionExecutor) {
        this.homePageGameService = homePageGameService;
        this.cheerService = cheerService;
        this.partyService = partyService;
        this.sectionTimeout = (sectionTimeout == null || sectionTimeout.isZero() || sectionTimeout.isNegative())
                ? DEFAULT_SECTION_TIMEOUT
                : sectionTimeout;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.sectionExecutor = sectionExecutor == null
                ? Executors.newVirtualThreadPerTaskExecutor()
                : sectionExecutor;
    }

    @Transactional(readOnly = true)
    public HomeBootstrapResponseDto getBootstrap(LocalDate date) {
        LocalDate selectedDate = date == null ? LocalDate.now(clock) : date;
        long bootstrapStartedAt = System.nanoTime();

        SectionTask<LeagueStartDatesDto> leagueStartDatesTask =
                submitSection(sectionExecutor, "leagueStartDates", () -> safeGetLeagueStartDates(selectedDate));
        SectionTask<HomeScheduleNavigationDto> navigationTask =
                submitSection(sectionExecutor, "navigation", () -> safeGetNavigation(selectedDate));
        SectionTask<List<HomePageGameDto>> gamesTask =
                submitSection(sectionExecutor, "games", () -> safeGetGames(selectedDate));
        SectionTask<List<HomePageScheduledGameDto>> scheduledGamesTask =
                submitSection(sectionExecutor, "scheduledGamesWindow", () -> safeGetScheduledGamesWindow(selectedDate));

        SectionResult<HomeScheduleNavigationDto> navigationResult =
                awaitSection(selectedDate, navigationTask, buildFallbackNavigation());
        SectionResult<List<HomePageGameDto>> gamesResult =
                awaitSection(selectedDate, gamesTask, List.of());
        SectionResult<List<HomePageScheduledGameDto>> scheduledGamesResult =
                awaitSection(selectedDate, scheduledGamesTask, List.of());
        SectionResult<LeagueStartDatesDto> leagueStartDatesResult =
                awaitSection(selectedDate, leagueStartDatesTask, buildFallbackLeagueStartDates(selectedDate));

        HomeBootstrapResponseDto response = HomeBootstrapResponseDto.builder()
                .selectedDate(selectedDate.toString())
                .leagueStartDates(leagueStartDatesResult.value())
                .navigation(navigationResult.value())
                .games(gamesResult.value())
                .scheduledGamesWindow(scheduledGamesResult.value())
                .build();

        int timedOutSections = 0;
        timedOutSections += leagueStartDatesResult.timedOut() ? 1 : 0;
        timedOutSections += navigationResult.timedOut() ? 1 : 0;
        timedOutSections += gamesResult.timedOut() ? 1 : 0;
        timedOutSections += scheduledGamesResult.timedOut() ? 1 : 0;

        log.info(
                "event=home_bootstrap_completed date={} totalElapsedMs={} sectionTimeoutMs={} timedOutSections={}",
                selectedDate,
                elapsedMillis(bootstrapStartedAt),
                sectionTimeout.toMillis(),
                timedOutSections);

        return response;
    }

    @PreDestroy
    void shutdownSectionExecutor() {
        sectionExecutor.shutdownNow();
    }

    @Cacheable(value = HOME_WIDGETS, key = "#date.toString() + ':' + (#seasonYear == null ? 'auto' : #seasonYear.toString())")
    @Transactional(readOnly = true)
    public HomeWidgetsResponseDto getWidgets(LocalDate date, Integer seasonYear) {
        List<PostSummaryRes> hotPosts;
        try {
            hotPosts = cheerService
                    .getHotPostsPublic(PageRequest.of(0, 3), "HYBRID")
                    .getContent();
        } catch (Exception e) {
            log.warn("Failed to load hot cheer posts: {}", e.getMessage());
            hotPosts = List.of();
        }

        List<FeaturedMateCardDto> featuredMates;
        try {
            featuredMates = partyService.getFeaturedMateCards(date, 4);
        } catch (Exception e) {
            log.warn("Failed to load featured mates: {}", e.getMessage());
            featuredMates = List.of();
        }

        return HomeWidgetsResponseDto.builder()
                .hotCheerPosts(hotPosts)
                .featuredMates(featuredMates)
                .rankingSnapshot(getRankingSnapshot(date, seasonYear))
                .build();
    }

    @Transactional(readOnly = true)
    public HomeRankingSnapshotDto getRankingSnapshot(LocalDate date, Integer seasonYear) {
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
        return new SectionTask<>(name, executor.submit(supplier::get), System.nanoTime());
    }

    private <T> SectionResult<T> awaitSection(LocalDate date, SectionTask<T> task, T fallbackValue) {
        long elapsedBeforeWaitMs = elapsedMillis(task.startedAtNanos());
        long remainingTimeoutMs = sectionTimeout.toMillis() - elapsedBeforeWaitMs;
        if (remainingTimeoutMs <= 0) {
            task.future().cancel(false);
            log.warn(
                    "event=home_bootstrap_section_timed_out date={} section={} elapsedMs={} timeoutMs={}",
                    date,
                    task.name(),
                    elapsedBeforeWaitMs,
                    sectionTimeout.toMillis());
            return new SectionResult<>(fallbackValue, true, elapsedBeforeWaitMs);
        }

        try {
            T value = task.future().get(remainingTimeoutMs, TimeUnit.MILLISECONDS);
            long elapsedMs = elapsedMillis(task.startedAtNanos());
            log.info(
                    "event=home_bootstrap_section_completed date={} section={} elapsedMs={} timeoutMs={}",
                    date,
                    task.name(),
                    elapsedMs,
                    sectionTimeout.toMillis());
            return new SectionResult<>(value, false, elapsedMs);
        } catch (TimeoutException ex) {
            task.future().cancel(false);
            long elapsedMs = elapsedMillis(task.startedAtNanos());
            log.warn(
                    "event=home_bootstrap_section_timed_out date={} section={} elapsedMs={} timeoutMs={}",
                    date,
                    task.name(),
                    elapsedMs,
                    sectionTimeout.toMillis());
            return new SectionResult<>(fallbackValue, true, elapsedMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            long elapsedMs = elapsedMillis(task.startedAtNanos());
            log.warn(
                    "event=home_bootstrap_section_interrupted date={} section={} elapsedMs={}",
                    date,
                    task.name(),
                    elapsedMs);
            return new SectionResult<>(fallbackValue, false, elapsedMs);
        } catch (ExecutionException ex) {
            long elapsedMs = elapsedMillis(task.startedAtNanos());
            Throwable cause = ex.getCause();
            if (cause instanceof ManualBaseballDataRequiredException manualDataRequiredException) {
                throw manualDataRequiredException;
            }
            log.warn(
                    "event=home_bootstrap_section_failed date={} section={} elapsedMs={} cause={}",
                    date,
                    task.name(),
                    elapsedMs,
                    cause == null ? ex.getMessage() : cause.getMessage());
            return new SectionResult<>(fallbackValue, false, elapsedMs);
        }
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
            LocalDate today = LocalDate.now(clock);
            LocalDate windowStartDate = date.isBefore(today) ? today : date;
            return homePageGameService.getScheduledGamesWindow(windowStartDate, windowStartDate.plusDays(7));
        } catch (ManualBaseballDataRequiredException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to load scheduled games window for date={}: {}", date, e.getMessage());
            return List.of();
        }
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

    private record SectionResult<T>(T value, boolean timedOut, long elapsedMs) {
    }
}
