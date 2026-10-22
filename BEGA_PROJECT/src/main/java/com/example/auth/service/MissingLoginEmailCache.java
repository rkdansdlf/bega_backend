package com.example.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;

/** Bounded short-lived cache for negative login lookups. */
final class MissingLoginEmailCache {

    static final int MAXIMUM_SIZE = 10_000;
    private static final Duration TTL = Duration.ofSeconds(30);

    private final Cache<String, Boolean> cache;
    private final Object generationLock = new Object();
    private long generation;

    MissingLoginEmailCache() {
        this(Ticker.systemTicker());
    }

    MissingLoginEmailCache(Ticker ticker) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(MAXIMUM_SIZE)
                .expireAfterWrite(TTL)
                .ticker(ticker)
                .build();
    }

    boolean contains(String normalizedEmail) {
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            return false;
        }
        synchronized (generationLock) {
            return Boolean.TRUE.equals(cache.getIfPresent(normalizedEmail));
        }
    }

    long generation() {
        synchronized (generationLock) {
            return generation;
        }
    }

    void record(String normalizedEmail) {
        recordIfGenerationUnchanged(normalizedEmail, generation());
    }

    void recordIfGenerationUnchanged(String normalizedEmail, long expectedGeneration) {
        if (normalizedEmail != null && !normalizedEmail.isBlank()) {
            synchronized (generationLock) {
                if (generation == expectedGeneration) {
                    cache.put(normalizedEmail, Boolean.TRUE);
                }
            }
        }
    }

    void evict(String normalizedEmail) {
        if (normalizedEmail != null && !normalizedEmail.isBlank()) {
            synchronized (generationLock) {
                generation++;
                cache.invalidate(normalizedEmail);
            }
        }
    }

    long estimatedSize() {
        return cache.estimatedSize();
    }

    void cleanUp() {
        cache.cleanUp();
    }
}
