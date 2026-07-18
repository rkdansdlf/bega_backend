package com.example.common.realtime.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class RealtimeAuthorizationCacheTest {

    private static final String KEY = "realtime:auth:party:10:123";

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final AtomicLong ticker = new AtomicLong();
    private RealtimeAuthorizationCache cache;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        cache = new RealtimeAuthorizationCache(redisTemplate, meterRegistry, true, ticker::get);
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
        meterRegistry.close();
    }

    @Test
    void readsAllowDenyAndMissWithoutConflatingFalseWithEmpty() {
        when(values.get(KEY)).thenReturn("allow", "deny", null);

        assertThat(cache.get(RealtimeAuthorizationResource.PARTY, 10L, 123L))
                .contains(true);
        assertThat(cache.get(RealtimeAuthorizationResource.PARTY, 10L, 123L))
                .contains(false);
        assertThat(cache.get(RealtimeAuthorizationResource.PARTY, 10L, 123L))
                .isEmpty();

        assertThat(counter("hit")).isEqualTo(2.0);
        assertThat(counter("miss")).isEqualTo(1.0);
    }

    @Test
    void writesBothDecisionsWithExactlyFiveSecondTtl() {
        cache.put(RealtimeAuthorizationResource.PARTY, 10L, 123L, true);
        cache.put(RealtimeAuthorizationResource.PARTY, 10L, 123L, false);

        verify(values).set(KEY, "allow", Duration.ofSeconds(5));
        verify(values).set(KEY, "deny", Duration.ofSeconds(5));
    }

    @Test
    void corruptValueIsDeletedAndReturnedAsMiss() {
        when(values.get(KEY)).thenReturn("unexpected");

        Optional<Boolean> result = cache.get(RealtimeAuthorizationResource.PARTY, 10L, 123L);

        assertThat(result).isEmpty();
        verify(redisTemplate).delete(KEY);
        assertThat(counter("corrupt")).isEqualTo(1.0);
    }

    @Test
    void corruptValueDeleteFailureOpensCircuit() {
        when(values.get(KEY)).thenReturn("unexpected");
        when(redisTemplate.delete(KEY)).thenThrow(new IllegalStateException("redis down"));

        assertThat(cache.get(RealtimeAuthorizationResource.PARTY, 10L, 123L)).isEmpty();
        clearInvocations(values);
        assertThat(cache.get(RealtimeAuthorizationResource.PARTY, 10L, 123L)).isEmpty();

        verify(values, never()).get(KEY);
        assertThat(counter("error")).isEqualTo(1.0);
        assertThat(counter("bypass")).isEqualTo(1.0);
    }

    @Test
    void redisReadFailureOpensCircuitAndSubsequentLookupBypassesRedis() {
        when(values.get(KEY)).thenThrow(new IllegalStateException("redis down"));

        assertThat(cache.get(RealtimeAuthorizationResource.PARTY, 10L, 123L)).isEmpty();
        assertThat(cache.get(RealtimeAuthorizationResource.PARTY, 10L, 123L)).isEmpty();

        verify(values, times(1)).get(KEY);
        assertThat(counter("error")).isEqualTo(1.0);
        assertThat(counter("bypass")).isEqualTo(1.0);
    }

    @Test
    void redisWriteFailureAlsoOpensCircuit() {
        doThrow(new IllegalStateException("redis down"))
                .when(values).set(KEY, "allow", Duration.ofSeconds(5));

        cache.put(RealtimeAuthorizationResource.PARTY, 10L, 123L, true);
        clearInvocations(values);
        assertThat(cache.get(RealtimeAuthorizationResource.PARTY, 10L, 123L)).isEmpty();

        verify(values, never()).get(KEY);
        assertThat(counter("error")).isEqualTo(1.0);
    }

    @Test
    void onlyOneHalfOpenProbeTouchesRedis() throws Exception {
        when(values.get(KEY)).thenThrow(new IllegalStateException("redis down"));
        cache.get(RealtimeAuthorizationResource.PARTY, 10L, 123L);
        ticker.addAndGet(Duration.ofSeconds(5).toNanos());

        reset(values);
        CountDownLatch probeEntered = new CountDownLatch(1);
        CountDownLatch releaseProbe = new CountDownLatch(1);
        when(values.get(KEY)).thenAnswer(invocation -> {
            probeEntered.countDown();
            assertTrue(releaseProbe.await(2, TimeUnit.SECONDS));
            return null;
        });

        executor = Executors.newFixedThreadPool(2);
        Future<Optional<Boolean>> probe = executor.submit(
                () -> cache.get(RealtimeAuthorizationResource.PARTY, 10L, 123L));
        assertTrue(probeEntered.await(2, TimeUnit.SECONDS));
        Future<Optional<Boolean>> bypass = executor.submit(
                () -> cache.get(RealtimeAuthorizationResource.PARTY, 10L, 123L));

        assertThat(bypass.get(2, TimeUnit.SECONDS)).isEmpty();
        releaseProbe.countDown();
        assertThat(probe.get(2, TimeUnit.SECONDS)).isEmpty();
        verify(values, times(1)).get(KEY);

        assertThat(cache.get(RealtimeAuthorizationResource.PARTY, 10L, 123L)).isEmpty();
        verify(values, times(2)).get(KEY);
    }

    @Test
    void staleHalfOpenProbeSuccessCannotCloseCircuitReopenedByOlderNormalFailure() throws Exception {
        CountDownLatch normalEntered = new CountDownLatch(1);
        CountDownLatch releaseNormal = new CountDownLatch(1);
        CountDownLatch probeEntered = new CountDownLatch(1);
        CountDownLatch releaseProbe = new CountDownLatch(1);
        AtomicInteger redisReads = new AtomicInteger();
        when(values.get(KEY)).thenAnswer(invocation -> {
            int read = redisReads.incrementAndGet();
            if (read == 1) {
                normalEntered.countDown();
                assertThat(releaseNormal.await(2, TimeUnit.SECONDS)).isTrue();
                throw new IllegalStateException("redis down");
            }
            if (read == 2) {
                throw new IllegalStateException("redis down");
            }
            if (read == 3) {
                probeEntered.countDown();
                assertThat(releaseProbe.await(2, TimeUnit.SECONDS)).isTrue();
                return "allow";
            }
            return null;
        });

        executor = Executors.newFixedThreadPool(2);
        Future<Optional<Boolean>> olderNormal = executor.submit(
                () -> cache.get(RealtimeAuthorizationResource.PARTY, 10L, 123L));
        assertThat(normalEntered.await(2, TimeUnit.SECONDS)).isTrue();

        assertThat(cache.get(RealtimeAuthorizationResource.PARTY, 10L, 123L)).isEmpty();
        ticker.addAndGet(Duration.ofSeconds(5).toNanos());

        Future<Optional<Boolean>> probe = executor.submit(
                () -> cache.get(RealtimeAuthorizationResource.PARTY, 10L, 123L));
        assertThat(probeEntered.await(2, TimeUnit.SECONDS)).isTrue();

        releaseNormal.countDown();
        assertThat(olderNormal.get(2, TimeUnit.SECONDS)).isEmpty();
        releaseProbe.countDown();
        assertThat(probe.get(2, TimeUnit.SECONDS)).contains(true);

        assertThat(cache.get(RealtimeAuthorizationResource.PARTY, 10L, 123L)).isEmpty();
        assertThat(redisReads).hasValue(3);
    }

    @Test
    void corruptHalfOpenProbeKeepsCircuitOpenUntilDeleteCompletesAndFailureReopens() throws Exception {
        when(values.get(KEY)).thenThrow(new IllegalStateException("redis down"));
        cache.get(RealtimeAuthorizationResource.PARTY, 10L, 123L);
        ticker.addAndGet(Duration.ofSeconds(5).toNanos());

        reset(values);
        when(values.get(KEY)).thenReturn("unexpected").thenReturn(null);
        CountDownLatch deleteEntered = new CountDownLatch(1);
        CountDownLatch releaseDelete = new CountDownLatch(1);
        when(redisTemplate.delete(KEY)).thenAnswer(invocation -> {
            deleteEntered.countDown();
            assertTrue(releaseDelete.await(2, TimeUnit.SECONDS));
            throw new IllegalStateException("redis down");
        });

        executor = Executors.newFixedThreadPool(2);
        Future<Optional<Boolean>> probe = executor.submit(
                () -> cache.get(RealtimeAuthorizationResource.PARTY, 10L, 123L));
        assertTrue(deleteEntered.await(2, TimeUnit.SECONDS));
        Future<Optional<Boolean>> bypass = executor.submit(
                () -> cache.get(RealtimeAuthorizationResource.PARTY, 10L, 123L));

        assertThat(bypass.get(2, TimeUnit.SECONDS)).isEmpty();
        verify(values, times(1)).get(KEY);
        releaseDelete.countDown();
        assertThat(probe.get(2, TimeUnit.SECONDS)).isEmpty();

        assertThat(cache.get(RealtimeAuthorizationResource.PARTY, 10L, 123L)).isEmpty();
        verify(values, times(1)).get(KEY);
        assertThat(counter("error")).isEqualTo(2.0);
        assertThat(counter("bypass")).isEqualTo(2.0);
    }

    @Test
    void failedHalfOpenProbeReopensCircuit() {
        when(values.get(KEY)).thenThrow(new IllegalStateException("redis down"));

        cache.get(RealtimeAuthorizationResource.PARTY, 10L, 123L);
        ticker.addAndGet(Duration.ofSeconds(5).toNanos());
        cache.get(RealtimeAuthorizationResource.PARTY, 10L, 123L);
        cache.get(RealtimeAuthorizationResource.PARTY, 10L, 123L);

        verify(values, times(2)).get(KEY);
    }

    @Test
    void disabledCacheNeverTouchesRedis() {
        cache = new RealtimeAuthorizationCache(redisTemplate, meterRegistry, false, ticker::get);

        assertThat(cache.get(RealtimeAuthorizationResource.PARTY, 10L, 123L)).isEmpty();
        cache.put(RealtimeAuthorizationResource.PARTY, 10L, 123L, true);

        verify(redisTemplate, never()).opsForValue();
        assertThat(counter("bypass")).isEqualTo(1.0);
    }

    @Test
    void cacheMetricsContainOnlyResourceAndOutcomeTags() {
        when(values.get(KEY)).thenReturn("allow");
        cache.get(RealtimeAuthorizationResource.PARTY, 10L, 123L);

        assertThat(meterRegistry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .map(Tag::getKey)
                .distinct()
                .toList())
                .containsExactlyInAnyOrder("resource", "outcome");
    }

    private double counter(String outcome) {
        return meterRegistry.get("realtime.authorization.cache")
                .tags("resource", "party", "outcome", outcome)
                .counter()
                .count();
    }
}
