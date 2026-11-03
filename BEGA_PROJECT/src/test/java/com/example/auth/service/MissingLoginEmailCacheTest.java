package com.example.auth.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class MissingLoginEmailCacheTest {

    @Test
    void expiresEntriesAfterThirtySecondsUsingTicker() {
        AtomicLong nowNanos = new AtomicLong();
        MissingLoginEmailCache cache = new MissingLoginEmailCache(nowNanos::get);

        cache.record("missing@example.com");
        assertTrue(cache.contains("missing@example.com"));

        nowNanos.addAndGet(Duration.ofSeconds(31).toNanos());

        assertFalse(cache.contains("missing@example.com"));
        cache.cleanUp();
        assertTrue(cache.estimatedSize() == 0);
    }

    @Test
    void concurrentUniqueEntriesRemainWithinConfiguredMaximum() {
        MissingLoginEmailCache cache = new MissingLoginEmailCache(Ticker.systemTicker());

        IntStream.range(0, MissingLoginEmailCache.MAXIMUM_SIZE * 2)
                .parallel()
                .forEach(index -> cache.record("missing-" + index + "@example.com"));

        cache.cleanUp();
        assertTrue(cache.estimatedSize() <= MissingLoginEmailCache.MAXIMUM_SIZE);
    }

    @Test
    void signupInvalidationRemovesTheCachedEmail() {
        MissingLoginEmailCache cache = new MissingLoginEmailCache(Ticker.systemTicker());
        cache.record("new-user@example.com");

        cache.evict("new-user@example.com");

        assertFalse(cache.contains("new-user@example.com"));
    }
}
