package com.example.homepage;

import static com.example.common.config.CacheConfig.HOME_BOOTSTRAP;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HomeBootstrapCacheService {

    private final CacheManager cacheManager;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, ReentrantLock> keyLocks = new ConcurrentHashMap<>();

    @Autowired
    public HomeBootstrapCacheService(CacheManager cacheManager, MeterRegistry meterRegistry) {
        this(cacheManager, Clock.systemDefaultZone(), meterRegistry);
    }

    HomeBootstrapCacheService(CacheManager cacheManager, Clock clock, MeterRegistry meterRegistry) {
        this.cacheManager = cacheManager;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.meterRegistry = meterRegistry == null ? Metrics.globalRegistry : meterRegistry;
    }

    public HomeBootstrapResponseDto getOrLoad(LocalDate date, Supplier<HomeBootstrapResponseDto> loader) {
        Objects.requireNonNull(loader, "loader must not be null");
        LocalDate selectedDate = resolveSelectedDate(date);
        String cacheKey = buildCacheKey(selectedDate);

        CacheLookup firstLookup = lookup(cacheKey);
        if (firstLookup.value() != null) {
            if (isCacheable(firstLookup.value())) {
                recordCacheEvent("lookup", "hit");
                return firstLookup.value();
            }
            recordCacheEvent("lookup", "stale");
            evict(cacheKey, "evict_stale");
        } else {
            recordCacheEvent("lookup", firstLookup.error() ? "error" : "miss");
        }

        ReentrantLock lock = keyLocks.computeIfAbsent(cacheKey, ignored -> new ReentrantLock());
        lock.lock();
        try {
            CacheLookup secondLookup = lookup(cacheKey);
            if (secondLookup.value() != null) {
                if (isCacheable(secondLookup.value())) {
                    recordCacheEvent("lookup", "hit");
                    return secondLookup.value();
                }
                recordCacheEvent("lookup", "stale");
                evict(cacheKey, "evict_stale");
            }
            if (secondLookup.error()) {
                recordCacheEvent("lookup", "error");
            }

            HomeBootstrapResponseDto response = loader.get();
            storeIfCacheable(cacheKey, response, "store");
            return response;
        } finally {
            lock.unlock();
        }
    }

    public HomeBootstrapResponseDto refresh(LocalDate date, Supplier<HomeBootstrapResponseDto> loader) {
        Objects.requireNonNull(loader, "loader must not be null");
        LocalDate selectedDate = resolveSelectedDate(date);
        String cacheKey = buildCacheKey(selectedDate);
        HomeBootstrapResponseDto response = loader.get();
        storeIfCacheable(cacheKey, response, "refresh");
        return response;
    }

    public String buildCacheKey(LocalDate date) {
        LocalDate selectedDate = resolveSelectedDate(date);
        LocalDate today = LocalDate.now(clock);
        return selectedDate + ":today:" + today;
    }

    private LocalDate resolveSelectedDate(LocalDate date) {
        return date == null ? LocalDate.now(clock) : date;
    }

    private CacheLookup lookup(String cacheKey) {
        Cache cache = getHomeBootstrapCache("lookup", cacheKey);
        if (cache == null) {
            return new CacheLookup(null, true);
        }

        try {
            return new CacheLookup(cache.get(cacheKey, HomeBootstrapResponseDto.class), false);
        } catch (RuntimeException ex) {
            log.warn(
                    "event=home_bootstrap_cache_lookup_failed cache={} key={} reason={}",
                    HOME_BOOTSTRAP,
                    cacheKey,
                    summarize(ex));
            return new CacheLookup(null, true);
        }
    }

    private void storeIfCacheable(String cacheKey, HomeBootstrapResponseDto response, String operation) {
        if (!isCacheable(response)) {
            recordCacheEvent(operation, "skipped");
            log.info("event=home_bootstrap_cache_store_skipped key={} operation={}", cacheKey, operation);
            return;
        }

        Cache cache = getHomeBootstrapCache(operation, cacheKey);
        if (cache == null) {
            return;
        }

        try {
            cache.put(cacheKey, response);
            recordCacheEvent(operation, "success");
        } catch (RuntimeException ex) {
            recordCacheEvent(operation, "error");
            log.warn(
                    "event=home_bootstrap_cache_store_failed cache={} key={} operation={} reason={}",
                    HOME_BOOTSTRAP,
                    cacheKey,
                    operation,
                    summarize(ex));
        }
    }

    private void evict(String cacheKey, String operation) {
        Cache cache = getHomeBootstrapCache(operation, cacheKey);
        if (cache == null) {
            return;
        }

        try {
            cache.evict(cacheKey);
            recordCacheEvent(operation, "success");
        } catch (RuntimeException ex) {
            recordCacheEvent(operation, "error");
            log.warn(
                    "event=home_bootstrap_cache_evict_failed cache={} key={} operation={} reason={}",
                    HOME_BOOTSTRAP,
                    cacheKey,
                    operation,
                    summarize(ex));
        }
    }

    private Cache getHomeBootstrapCache(String operation, String cacheKey) {
        try {
            Cache cache = cacheManager.getCache(HOME_BOOTSTRAP);
            if (cache == null) {
                recordCacheEvent(operation, "error");
                log.warn("event=home_bootstrap_cache_missing cache={} key={}", HOME_BOOTSTRAP, cacheKey);
            }
            return cache;
        } catch (RuntimeException ex) {
            recordCacheEvent(operation, "error");
            log.warn(
                    "event=home_bootstrap_cache_unavailable cache={} key={} reason={}",
                    HOME_BOOTSTRAP,
                    cacheKey,
                    summarize(ex));
            return null;
        }
    }

    private boolean isCacheable(HomeBootstrapResponseDto response) {
        return response != null && response.getLoadState() != null;
    }

    private void recordCacheEvent(String operation, String result) {
        Counter.builder("home_bootstrap_cache_events_total")
                .description("Home bootstrap cache events")
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

    private record CacheLookup(HomeBootstrapResponseDto value, boolean error) {
    }
}
