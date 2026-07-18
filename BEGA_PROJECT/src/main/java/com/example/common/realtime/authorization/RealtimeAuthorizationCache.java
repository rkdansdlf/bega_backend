package com.example.common.realtime.authorization;

import java.time.Duration;
import java.util.Optional;
import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RealtimeAuthorizationCache {

    static final Duration ENTRY_TTL = Duration.ofSeconds(5);
    static final Duration CIRCUIT_OPEN_DURATION = Duration.ofSeconds(5);
    private static final long CIRCUIT_CLOSED = Long.MIN_VALUE;
    private static final String ALLOW = "allow";
    private static final String DENY = "deny";

    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;
    private final boolean enabled;
    private final LongSupplier ticker;
    private final Object circuitMonitor = new Object();

    private long openUntilNanos = CIRCUIT_CLOSED;
    private long circuitGeneration;
    private boolean probeInFlight;

    @Autowired
    public RealtimeAuthorizationCache(
            StringRedisTemplate redisTemplate,
            MeterRegistry meterRegistry,
            @Value("${app.realtime.authorization-cache.enabled:true}") boolean enabled) {
        this(redisTemplate, meterRegistry, enabled, System::nanoTime);
    }

    RealtimeAuthorizationCache(
            StringRedisTemplate redisTemplate,
            MeterRegistry meterRegistry,
            boolean enabled,
            LongSupplier ticker) {
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
        this.enabled = enabled;
        this.ticker = ticker;
    }

    public Optional<Boolean> get(
            RealtimeAuthorizationResource resource,
            Long resourceId,
            Long userId) {
        CircuitAccess access = acquire(resource, true);
        if (access.mode() == AccessMode.BYPASS) {
            return Optional.empty();
        }

        String cacheKey = key(resource, resourceId, userId);
        final String raw;
        try {
            raw = redisTemplate.opsForValue().get(cacheKey);
        } catch (RuntimeException exception) {
            onRedisFailure(access, resource, exception);
            return Optional.empty();
        }

        if (raw == null) {
            onRedisSuccess(access, resource);
            record(resource, "miss");
            return Optional.empty();
        }
        if (ALLOW.equals(raw)) {
            onRedisSuccess(access, resource);
            record(resource, "hit");
            return Optional.of(true);
        }
        if (DENY.equals(raw)) {
            onRedisSuccess(access, resource);
            record(resource, "hit");
            return Optional.of(false);
        }

        record(resource, "corrupt");
        try {
            redisTemplate.delete(cacheKey);
            onRedisSuccess(access, resource);
        } catch (RuntimeException exception) {
            onRedisFailure(access, resource, exception);
        }
        return Optional.empty();
    }

    public void put(
            RealtimeAuthorizationResource resource,
            Long resourceId,
            Long userId,
            boolean allowed) {
        CircuitAccess access = acquire(resource, false);
        if (access.mode() == AccessMode.BYPASS) {
            return;
        }

        try {
            redisTemplate.opsForValue().set(
                    key(resource, resourceId, userId),
                    allowed ? ALLOW : DENY,
                    ENTRY_TTL);
            onRedisSuccess(access, resource);
        } catch (RuntimeException exception) {
            onRedisFailure(access, resource, exception);
        }
    }

    static String key(
            RealtimeAuthorizationResource resource,
            Long resourceId,
            Long userId) {
        if (resource == null || resourceId == null || userId == null) {
            throw new IllegalArgumentException("Realtime authorization cache key fields are required");
        }
        return "realtime:auth:" + resource.tag() + ":" + resourceId + ":" + userId;
    }

    private CircuitAccess acquire(RealtimeAuthorizationResource resource, boolean recordBypass) {
        if (!enabled) {
            if (recordBypass) {
                record(resource, "bypass");
            }
            return new CircuitAccess(AccessMode.BYPASS, 0L);
        }

        synchronized (circuitMonitor) {
            if (openUntilNanos == CIRCUIT_CLOSED) {
                return new CircuitAccess(AccessMode.NORMAL, 0L);
            }
            if (ticker.getAsLong() < openUntilNanos) {
                if (recordBypass) {
                    record(resource, "bypass");
                }
                return new CircuitAccess(AccessMode.BYPASS, 0L);
            }
            if (!probeInFlight) {
                probeInFlight = true;
                return new CircuitAccess(AccessMode.PROBE, circuitGeneration);
            }
            if (recordBypass) {
                record(resource, "bypass");
            }
            return new CircuitAccess(AccessMode.BYPASS, 0L);
        }
    }

    private void onRedisSuccess(CircuitAccess access, RealtimeAuthorizationResource resource) {
        if (access.mode() != AccessMode.PROBE) {
            return;
        }
        boolean closed;
        synchronized (circuitMonitor) {
            probeInFlight = false;
            closed = circuitGeneration == access.probeGeneration();
            if (closed) {
                openUntilNanos = CIRCUIT_CLOSED;
            }
        }
        if (closed) {
            log.info("Realtime authorization Redis circuit closed resource={}", resource.tag());
        }
    }

    private void onRedisFailure(
            CircuitAccess access,
            RealtimeAuthorizationResource resource,
            RuntimeException exception) {
        record(resource, "error");
        boolean transitioned = false;
        synchronized (circuitMonitor) {
            long now = ticker.getAsLong();
            if (openUntilNanos == CIRCUIT_CLOSED
                    || access.mode() == AccessMode.PROBE
                    || now >= openUntilNanos) {
                openUntilNanos = now + CIRCUIT_OPEN_DURATION.toNanos();
                circuitGeneration++;
                transitioned = true;
            }
            if (access.mode() == AccessMode.PROBE) {
                probeInFlight = false;
            }
        }
        if (transitioned) {
            log.warn(
                    "Realtime authorization Redis circuit opened resource={} reason={}",
                    resource.tag(),
                    exception.getClass().getSimpleName());
        }
    }

    private void record(RealtimeAuthorizationResource resource, String outcome) {
        meterRegistry.counter(
                "realtime.authorization.cache",
                "resource", resource.tag(),
                "outcome", outcome).increment();
    }

    private enum AccessMode {
        NORMAL,
        PROBE,
        BYPASS
    }

    private record CircuitAccess(AccessMode mode, long probeGeneration) {
    }
}
