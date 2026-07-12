package com.example.homepage;

import static com.example.common.config.CacheConfig.HOME_WIDGETS;

import com.example.common.cache.BoundedLocalCache;
import com.example.common.concurrent.StripedLockRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HomeWidgetsCacheService {

    private static final Duration FALLBACK_CACHE_TTL = Duration.ofSeconds(10);
    private static final int LOCAL_CACHE_MAX_ENTRIES = 256;
    private static final int KEY_LOCK_STRIPES = 64;

    private final CacheManager cacheManager;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final StripedLockRegistry keyLocks = new StripedLockRegistry(KEY_LOCK_STRIPES);
    private final BoundedLocalCache<String, CachedFallbackWidgets> fallbackWidgets =
            new BoundedLocalCache<>(LOCAL_CACHE_MAX_ENTRIES);

    @Autowired
    public HomeWidgetsCacheService(CacheManager cacheManager, MeterRegistry meterRegistry) {
        this(cacheManager, Clock.systemDefaultZone(), meterRegistry);
    }

    HomeWidgetsCacheService(CacheManager cacheManager, Clock clock, MeterRegistry meterRegistry) {
        this.cacheManager = cacheManager;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.meterRegistry = meterRegistry == null ? Metrics.globalRegistry : meterRegistry;
    }

    public HomeWidgetsResponseDto getOrLoad(
            LocalDate date,
            Integer seasonYear,
            Supplier<HomeWidgetsResponseDto> loader,
            Predicate<HomeWidgetsResponseDto> uncacheableResponse) {
        Objects.requireNonNull(loader, "loader must not be null");
        Objects.requireNonNull(uncacheableResponse, "uncacheableResponse must not be null");

        LocalDate selectedDate = resolveSelectedDate(date);
        String cacheKey = buildCacheKey(selectedDate, seasonYear);

        HomeWidgetsResponseDto cachedResponse = lookup(cacheKey);
        if (cachedResponse != null) {
            if (!uncacheableResponse.test(cachedResponse)) {
                recordCacheEvent("lookup", "hit");
                return cachedResponse;
            }
            recordCacheEvent("lookup", "stale");
            evict(cacheKey, "evict_stale");
        } else {
            recordCacheEvent("lookup", "miss");
        }
        HomeWidgetsResponseDto firstFallback = getCachedFallback(cacheKey);
        if (firstFallback != null) {
            return firstFallback;
        }

        Lock lock = keyLocks.lockFor(cacheKey);
        lock.lock();
        try {
            HomeWidgetsResponseDto secondLookup = lookup(cacheKey);
            if (secondLookup != null) {
                if (!uncacheableResponse.test(secondLookup)) {
                    recordCacheEvent("lookup", "hit");
                    return secondLookup;
                }
                recordCacheEvent("lookup", "stale");
                evict(cacheKey, "evict_stale");
            }
            HomeWidgetsResponseDto secondFallback = getCachedFallback(cacheKey);
            if (secondFallback != null) {
                return secondFallback;
            }

            HomeWidgetsResponseDto response = loader.get();
            if (uncacheableResponse.test(response)) {
                storeFallback(cacheKey, response);
                return response;
            }
            fallbackWidgets.remove(cacheKey);
            store(cacheKey, response);
            return response;
        } finally {
            lock.unlock();
        }
    }

    public String buildCacheKey(LocalDate date, Integer seasonYear) {
        LocalDate selectedDate = resolveSelectedDate(date);
        return selectedDate + ":" + (seasonYear == null ? "auto" : seasonYear.toString());
    }

    private HomeWidgetsResponseDto getCachedFallback(String cacheKey) {
        CachedFallbackWidgets cached = fallbackWidgets.get(cacheKey);
        if (cached == null) {
            recordCacheEvent("fallback_lookup", "miss");
            return null;
        }
        long nowMillis = clock.millis();
        if (nowMillis >= cached.expiresAtMillis()) {
            fallbackWidgets.remove(cacheKey, cached);
            recordCacheEvent("fallback_lookup", "expired");
            return null;
        }
        recordCacheEvent("fallback_lookup", "hit");
        return cached.response();
    }

    private LocalDate resolveSelectedDate(LocalDate date) {
        return date == null ? LocalDate.now(clock) : date;
    }

    private HomeWidgetsResponseDto lookup(String cacheKey) {
        Cache cache = getHomeWidgetsCache("lookup", cacheKey);
        if (cache == null) {
            return null;
        }

        try {
            return cache.get(cacheKey, HomeWidgetsResponseDto.class);
        } catch (RuntimeException ex) {
            recordCacheEvent("lookup", "error");
            log.warn(
                    "event=home_widgets_cache_lookup_failed cache={} key={} reason={}",
                    HOME_WIDGETS,
                    cacheKey,
                    summarize(ex));
            return null;
        }
    }

    private void store(String cacheKey, HomeWidgetsResponseDto response) {
        Cache cache = getHomeWidgetsCache("store", cacheKey);
        if (cache == null) {
            return;
        }

        try {
            cache.put(cacheKey, response);
            recordCacheEvent("store", "success");
        } catch (RuntimeException ex) {
            recordCacheEvent("store", "error");
            log.warn(
                    "event=home_widgets_cache_store_failed cache={} key={} reason={}",
                    HOME_WIDGETS,
                    cacheKey,
                    summarize(ex));
        }
    }

    private void storeFallback(String cacheKey, HomeWidgetsResponseDto response) {
        if (response == null) {
            recordCacheEvent("store", "skipped");
            recordCacheEvent("fallback_store", "skipped");
            return;
        }
        fallbackWidgets.put(
                cacheKey,
                new CachedFallbackWidgets(response, clock.millis() + FALLBACK_CACHE_TTL.toMillis()));
        recordCacheEvent("store", "skipped");
        recordCacheEvent("fallback_store", "success");
        log.info("event=home_widgets_fallback_cache_store key={}", cacheKey);
    }

    private void evict(String cacheKey, String operation) {
        Cache cache = getHomeWidgetsCache(operation, cacheKey);
        if (cache == null) {
            return;
        }

        try {
            cache.evict(cacheKey);
            recordCacheEvent(operation, "success");
        } catch (RuntimeException ex) {
            recordCacheEvent(operation, "error");
            log.warn(
                    "event=home_widgets_cache_evict_failed cache={} key={} operation={} reason={}",
                    HOME_WIDGETS,
                    cacheKey,
                    operation,
                    summarize(ex));
        }
    }

    private Cache getHomeWidgetsCache(String operation, String cacheKey) {
        try {
            Cache cache = cacheManager.getCache(HOME_WIDGETS);
            if (cache == null) {
                recordCacheEvent(operation, "error");
                log.warn("event=home_widgets_cache_missing cache={} key={}", HOME_WIDGETS, cacheKey);
            }
            return cache;
        } catch (RuntimeException ex) {
            recordCacheEvent(operation, "error");
            log.warn(
                    "event=home_widgets_cache_unavailable cache={} key={} reason={}",
                    HOME_WIDGETS,
                    cacheKey,
                    summarize(ex));
            return null;
        }
    }

    private void recordCacheEvent(String operation, String result) {
        Counter.builder("home_widgets_cache_events_total")
                .description("Home widgets cache events")
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

        return message;
    }

    private record CachedFallbackWidgets(HomeWidgetsResponseDto response, long expiresAtMillis) {
    }
}
