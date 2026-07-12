package com.example.homepage;

import static com.example.common.config.CacheConfig.HOME_BOOTSTRAP;

import com.example.common.cache.BoundedLocalCache;
import com.example.common.concurrent.StripedLockRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HomeBootstrapCacheService {

    private static final Duration FALLBACK_CACHE_TTL = Duration.ofSeconds(10);
    private static final Duration STALE_SUCCESS_TTL = Duration.ofMinutes(2);
    private static final Duration STALE_REFRESH_WINDOW = Duration.ofSeconds(30);
    private static final int STALE_CLEANUP_THRESHOLD = 64;
    private static final int LOCAL_CACHE_MAX_ENTRIES = 256;
    private static final int KEY_LOCK_STRIPES = 64;
    private static final String MANUAL_BASEBALL_DATA_REQUIRED = "MANUAL_BASEBALL_DATA_REQUIRED";

    private final CacheManager cacheManager;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final StripedLockRegistry keyLocks = new StripedLockRegistry(KEY_LOCK_STRIPES);
    private final BoundedLocalCache<String, CachedFallbackBootstrap> fallbackBootstraps =
            new BoundedLocalCache<>(LOCAL_CACHE_MAX_ENTRIES);
    private final BoundedLocalCache<String, CachedStaleBootstrap> staleBootstraps =
            new BoundedLocalCache<>(LOCAL_CACHE_MAX_ENTRIES);

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
                storeStale(cacheKey, firstLookup.value());
                return firstLookup.value();
            }
            recordCacheEvent("lookup", "stale");
            evict(cacheKey, "evict_stale");
        } else {
            recordCacheEvent("lookup", firstLookup.error() ? "error" : "miss");
        }
        HomeBootstrapResponseDto firstFallback = getCachedFallback(cacheKey);
        if (firstFallback != null) {
            return resolveFallbackResponse(cacheKey, firstFallback, "fallback_cache");
        }

        Lock lock = keyLocks.lockFor(cacheKey);
        lock.lock();
        try {
            CacheLookup secondLookup = lookup(cacheKey);
            if (secondLookup.value() != null) {
                if (isCacheable(secondLookup.value())) {
                    recordCacheEvent("lookup", "hit");
                    storeStale(cacheKey, secondLookup.value());
                    return secondLookup.value();
                }
                recordCacheEvent("lookup", "stale");
                evict(cacheKey, "evict_stale");
            }
            if (secondLookup.error()) {
                recordCacheEvent("lookup", "error");
            }
            HomeBootstrapResponseDto secondFallback = getCachedFallback(cacheKey);
            if (secondFallback != null) {
                return resolveFallbackResponse(cacheKey, secondFallback, "fallback_cache");
            }

            HomeBootstrapResponseDto response = loader.get();
            storeLoadedResponse(cacheKey, response, "store");
            return resolveFallbackResponse(cacheKey, response, "loader");
        } finally {
            lock.unlock();
        }
    }

    public HomeBootstrapResponseDto refresh(LocalDate date, Supplier<HomeBootstrapResponseDto> loader) {
        Objects.requireNonNull(loader, "loader must not be null");
        LocalDate selectedDate = resolveSelectedDate(date);
        String cacheKey = buildCacheKey(selectedDate);
        HomeBootstrapResponseDto response = loader.get();
        storeLoadedResponse(cacheKey, response, "refresh");
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

    private HomeBootstrapResponseDto getCachedFallback(String cacheKey) {
        CachedFallbackBootstrap cached = fallbackBootstraps.get(cacheKey);
        if (cached == null) {
            recordCacheEvent("fallback_lookup", "miss");
            return null;
        }
        long nowMillis = clock.millis();
        if (nowMillis >= cached.expiresAtMillis()) {
            fallbackBootstraps.remove(cacheKey, cached);
            recordCacheEvent("fallback_lookup", "expired");
            return null;
        }
        recordCacheEvent("fallback_lookup", "hit");
        return cached.response();
    }

    private void storeLoadedResponse(String cacheKey, HomeBootstrapResponseDto response, String operation) {
        if (isCacheable(response)) {
            fallbackBootstraps.remove(cacheKey);
            storeStale(cacheKey, response);
            storeIfCacheable(cacheKey, response, operation);
            return;
        }
        storeFallback(cacheKey, response, operation);
    }

    private HomeBootstrapResponseDto resolveFallbackResponse(
            String cacheKey, HomeBootstrapResponseDto response, String source) {
        if (response == null || isCacheable(response)) {
            return response;
        }
        if (isManualDataRequired(response)) {
            recordCacheEvent("stale_lookup", "skipped_manual_data");
            return response;
        }

        HomeBootstrapResponseDto staleResponse = getCachedStale(cacheKey);
        if (staleResponse == null) {
            return response;
        }

        log.info("event=home_bootstrap_stale_success_served key={} source={}", cacheKey, source);
        return staleResponse;
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

    private void storeFallback(String cacheKey, HomeBootstrapResponseDto response, String operation) {
        if (response == null) {
            recordCacheEvent(operation, "skipped");
            recordCacheEvent("fallback_store", "skipped");
            return;
        }

        fallbackBootstraps.put(
                cacheKey,
                new CachedFallbackBootstrap(response, clock.millis() + FALLBACK_CACHE_TTL.toMillis()));
        recordCacheEvent(operation, "skipped");
        recordCacheEvent("fallback_store", "success");
        log.info("event=home_bootstrap_fallback_cache_store key={} operation={}", cacheKey, operation);
    }

    private HomeBootstrapResponseDto getCachedStale(String cacheKey) {
        CachedStaleBootstrap cached = staleBootstraps.get(cacheKey);
        if (cached == null) {
            recordCacheEvent("stale_lookup", "miss");
            return null;
        }
        long nowMillis = clock.millis();
        if (nowMillis >= cached.expiresAtMillis()) {
            staleBootstraps.remove(cacheKey, cached);
            recordCacheEvent("stale_lookup", "expired");
            return null;
        }
        recordCacheEvent("stale_lookup", "hit");
        return cached.response();
    }

    private void storeStale(String cacheKey, HomeBootstrapResponseDto response) {
        if (!isCacheable(response)) {
            recordCacheEvent("stale_store", "skipped");
            return;
        }

        long nowMillis = clock.millis();
        CachedStaleBootstrap current = staleBootstraps.get(cacheKey);
        if (current != null && nowMillis + STALE_REFRESH_WINDOW.toMillis() < current.expiresAtMillis()) {
            return;
        }
        if (staleBootstraps.size() >= STALE_CLEANUP_THRESHOLD) {
            removeExpiredStaleBootstraps(nowMillis);
        }
        staleBootstraps.put(
                cacheKey,
                new CachedStaleBootstrap(response, nowMillis + STALE_SUCCESS_TTL.toMillis()));
        recordCacheEvent("stale_store", "success");
    }

    private void removeExpiredStaleBootstraps(long nowMillis) {
        staleBootstraps.removeIf((key, value) -> nowMillis >= value.expiresAtMillis());
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

    boolean isCacheable(HomeBootstrapResponseDto response) {
        if (response == null || response.getLoadState() == null) {
            return false;
        }
        HomeBootstrapLoadStateDto loadState = response.getLoadState();
        return !Boolean.TRUE.equals(loadState.getIsFallback())
                && !Boolean.TRUE.equals(loadState.getTimedOut())
                && !hasSections(loadState.getTimedOutSections())
                && !hasSections(loadState.getFailedSections())
                && loadState.getManualDataRequest() == null
                && (loadState.getFailureReason() == null || loadState.getFailureReason().isBlank());
    }

    private boolean isManualDataRequired(HomeBootstrapResponseDto response) {
        if (response == null || response.getLoadState() == null) {
            return false;
        }
        HomeBootstrapLoadStateDto loadState = response.getLoadState();
        return loadState.getManualDataRequest() != null
                || MANUAL_BASEBALL_DATA_REQUIRED.equals(loadState.getFailureReason());
    }

    private boolean hasSections(List<String> sections) {
        return sections != null && !sections.isEmpty();
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

    private record CachedFallbackBootstrap(HomeBootstrapResponseDto response, long expiresAtMillis) {
    }

    private record CachedStaleBootstrap(HomeBootstrapResponseDto response, long expiresAtMillis) {
    }
}
