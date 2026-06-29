package com.example.homepage;

import static com.example.common.config.CacheConfig.HOME_RANKING_SNAPSHOT;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HomeRankingSnapshotCacheService {

    private static final String RANKING_FALLBACK_SOURCE_MESSAGE = "순위 데이터를 불러오지 못했습니다.";
    private static final Duration FALLBACK_CACHE_TTL = Duration.ofSeconds(60);

    private final CacheManager cacheManager;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, ReentrantLock> keyLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CachedFallbackSnapshot> fallbackSnapshots = new ConcurrentHashMap<>();

    @Autowired
    public HomeRankingSnapshotCacheService(CacheManager cacheManager, MeterRegistry meterRegistry) {
        this(cacheManager, Clock.systemDefaultZone(), meterRegistry);
    }

    HomeRankingSnapshotCacheService(CacheManager cacheManager, Clock clock, MeterRegistry meterRegistry) {
        this.cacheManager = cacheManager;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.meterRegistry = meterRegistry == null ? Metrics.globalRegistry : meterRegistry;
    }

    public HomeRankingSnapshotDto getOrLoad(
            LocalDate date,
            Integer seasonYear,
            Supplier<HomeRankingSnapshotDto> loader) {
        Objects.requireNonNull(loader, "loader must not be null");
        LocalDate selectedDate = resolveSelectedDate(date);
        String cacheKey = buildCacheKey(selectedDate, seasonYear);
        long startedAtNanos = System.nanoTime();

        CacheLookup firstLookup = lookup(cacheKey);
        if (firstLookup.value() != null) {
            recordCacheEvent("lookup", "hit");
            recordSnapshotDuration(selectedDate, seasonYear, "hit", firstLookup.value(), startedAtNanos);
            return firstLookup.value();
        }
        recordCacheEvent("lookup", firstLookup.error() ? "error" : "miss");
        HomeRankingSnapshotDto firstFallback = getCachedFallback(cacheKey);
        if (firstFallback != null) {
            recordCacheEvent("fallback_lookup", "hit");
            recordSnapshotDuration(selectedDate, seasonYear, "fallback_hit", firstFallback, startedAtNanos);
            return firstFallback;
        }

        ReentrantLock lock = keyLocks.computeIfAbsent(cacheKey, ignored -> new ReentrantLock());
        lock.lock();
        try {
            CacheLookup secondLookup = lookup(cacheKey);
            if (secondLookup.value() != null) {
                recordCacheEvent("lookup", "hit");
                recordSnapshotDuration(selectedDate, seasonYear, "hit", secondLookup.value(), startedAtNanos);
                return secondLookup.value();
            }
            if (secondLookup.error()) {
                recordCacheEvent("lookup", "error");
            }
            HomeRankingSnapshotDto secondFallback = getCachedFallback(cacheKey);
            if (secondFallback != null) {
                recordCacheEvent("fallback_lookup", "hit");
                recordSnapshotDuration(selectedDate, seasonYear, "fallback_hit", secondFallback, startedAtNanos);
                return secondFallback;
            }

            HomeRankingSnapshotDto response = loader.get();
            storeLoadedResponse(cacheKey, response);
            recordSnapshotDuration(selectedDate, seasonYear, "miss", response, startedAtNanos);
            return response;
        } finally {
            lock.unlock();
        }
    }

    public String buildCacheKey(LocalDate date, Integer seasonYear) {
        if (seasonYear != null) {
            return "season:" + seasonYear;
        }
        return "auto:" + resolveSelectedDate(date);
    }

    private LocalDate resolveSelectedDate(LocalDate date) {
        return date == null ? LocalDate.now(clock) : date;
    }

    private HomeRankingSnapshotDto getCachedFallback(String cacheKey) {
        CachedFallbackSnapshot cached = fallbackSnapshots.get(cacheKey);
        if (cached == null) {
            return null;
        }
        long nowMillis = clock.millis();
        if (nowMillis >= cached.expiresAtMillis()) {
            fallbackSnapshots.remove(cacheKey, cached);
            recordCacheEvent("fallback_lookup", "expired");
            return null;
        }
        return cached.snapshot();
    }

    private CacheLookup lookup(String cacheKey) {
        Cache cache = getRankingSnapshotCache("lookup", cacheKey);
        if (cache == null) {
            return new CacheLookup(null, true);
        }

        try {
            return new CacheLookup(cache.get(cacheKey, HomeRankingSnapshotDto.class), false);
        } catch (RuntimeException ex) {
            log.warn(
                    "event=home_ranking_snapshot_cache_lookup_failed cache={} key={} reason={}",
                    HOME_RANKING_SNAPSHOT,
                    cacheKey,
                    summarize(ex));
            return new CacheLookup(null, true);
        }
    }

    private void storeIfCacheable(String cacheKey, HomeRankingSnapshotDto response) {
        if (!isCacheable(response)) {
            recordCacheEvent("store", "skipped");
            log.info("event=home_ranking_snapshot_cache_store_skipped key={}", cacheKey);
            return;
        }

        Cache cache = getRankingSnapshotCache("store", cacheKey);
        if (cache == null) {
            return;
        }

        try {
            cache.put(cacheKey, response);
            recordCacheEvent("store", "success");
        } catch (RuntimeException ex) {
            recordCacheEvent("store", "error");
            log.warn(
                    "event=home_ranking_snapshot_cache_store_failed cache={} key={} reason={}",
                    HOME_RANKING_SNAPSHOT,
                    cacheKey,
                    summarize(ex));
        }
    }

    private void storeLoadedResponse(String cacheKey, HomeRankingSnapshotDto response) {
        if (isCacheable(response)) {
            fallbackSnapshots.remove(cacheKey);
            storeIfCacheable(cacheKey, response);
            return;
        }
        storeFallback(cacheKey, response);
    }

    private void storeFallback(String cacheKey, HomeRankingSnapshotDto response) {
        if (response == null) {
            recordCacheEvent("fallback_store", "skipped");
            return;
        }
        fallbackSnapshots.put(
                cacheKey,
                new CachedFallbackSnapshot(response, clock.millis() + FALLBACK_CACHE_TTL.toMillis()));
        recordCacheEvent("fallback_store", "success");
        log.info("event=home_ranking_snapshot_fallback_cache_store key={}", cacheKey);
    }

    boolean isCacheable(HomeRankingSnapshotDto response) {
        return response != null
                && response.getRankingSourceMessage() != null
                && !RANKING_FALLBACK_SOURCE_MESSAGE.equals(response.getRankingSourceMessage());
    }

    private Cache getRankingSnapshotCache(String operation, String cacheKey) {
        try {
            Cache cache = cacheManager.getCache(HOME_RANKING_SNAPSHOT);
            if (cache == null) {
                recordCacheEvent(operation, "error");
                log.warn("event=home_ranking_snapshot_cache_missing cache={} key={}", HOME_RANKING_SNAPSHOT, cacheKey);
            }
            return cache;
        } catch (RuntimeException ex) {
            recordCacheEvent(operation, "error");
            log.warn(
                    "event=home_ranking_snapshot_cache_unavailable cache={} key={} reason={}",
                    HOME_RANKING_SNAPSHOT,
                    cacheKey,
                    summarize(ex));
            return null;
        }
    }

    private void recordSnapshotDuration(
            LocalDate date,
            Integer seasonYear,
            String cacheResult,
            HomeRankingSnapshotDto response,
            long startedAtNanos) {
        long elapsedNanos = System.nanoTime() - startedAtNanos;
        if (elapsedNanos < 0) {
            return;
        }

        boolean fallback = !isCacheable(response);
        int rankingCount = response == null || response.getRankings() == null ? 0 : response.getRankings().size();
        String mode = seasonYear == null ? "auto" : "season";
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);

        Timer.builder("home_ranking_snapshot_duration_seconds")
                .description("Home ranking snapshot load duration")
                .publishPercentileHistogram()
                .tags(
                        "mode", mode,
                        "cache_result", normalizeMetricTag(cacheResult),
                        "fallback", Boolean.toString(fallback))
                .register(meterRegistry)
                .record(elapsedNanos, TimeUnit.NANOSECONDS);

        log.info(
                "event=home_ranking_snapshot_completed date={} mode={} seasonYear={} rankingCount={} fallback={} cacheResult={} elapsedMs={}",
                date,
                mode,
                seasonYear,
                rankingCount,
                fallback,
                cacheResult,
                elapsedMs);
    }

    private void recordCacheEvent(String operation, String result) {
        Counter.builder("home_ranking_snapshot_cache_events_total")
                .description("Home ranking snapshot cache events")
                .tags(
                        "operation", normalizeMetricTag(operation),
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

    private String summarize(RuntimeException exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }

        String message = rootCause.getMessage();
        if (message == null || message.isBlank()) {
            message = rootCause.getClass().getSimpleName();
        }

        String normalized = message.replaceAll("\\s+", " ").trim();
        return normalized.length() > 220 ? normalized.substring(0, 220) + "..." : normalized;
    }

    private record CacheLookup(HomeRankingSnapshotDto value, boolean error) {
    }

    private record CachedFallbackSnapshot(HomeRankingSnapshotDto snapshot, long expiresAtMillis) {
    }
}
