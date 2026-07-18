package com.example.ai.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AiIngestMonitoringMetrics {

    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "SUCCEEDED",
            "FAILED",
            "MANUAL_BASEBALL_DATA_REQUIRED",
            "MONITOR_TIMEOUT");
    private static final Duration OBSERVATION_TTL = Duration.ofDays(7);
    private static final String OBSERVATION_KEY_PREFIX = "ai-ingest:metrics:observed:";

    private final MeterRegistry meterRegistry;
    private final StringRedisTemplate redisTemplate;
    private final Cache<String, Boolean> observedLocalKeys = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(OBSERVATION_TTL)
            .build();

    @Autowired
    public AiIngestMonitoringMetrics(
            MeterRegistry meterRegistry,
            StringRedisTemplate redisTemplate) {
        this.meterRegistry = meterRegistry;
        this.redisTemplate = redisTemplate;
    }

    AiIngestMonitoringMetrics(MeterRegistry meterRegistry) {
        this(meterRegistry, null);
    }

    public void recordSubmissionAccepted(boolean deduplicated) {
        recordSafely("submission", () -> meterRegistry.counter(
                                "backend_ai_ingest_submissions_total",
                                "result", "accepted",
                                "deduplicated", Boolean.toString(deduplicated))
                        .increment());
    }

    public void recordSubmissionFailure() {
        recordSafely("submission", () -> meterRegistry.counter(
                                "backend_ai_ingest_submissions_total",
                                "result", "failure",
                                "deduplicated", "unknown")
                        .increment());
    }

    public boolean recordTerminal(UUID runId, String status, Duration elapsed) {
        String statusLabel = normalizeTerminalStatus(status);
        ObservationClaim claim = claimObservation(
                "terminal:" + statusLabel,
                runId);
        if (claim == null) {
            return false;
        }
        Duration safeElapsed = nonNegative(elapsed);
        boolean counted = recordSafely("terminal_count", () -> meterRegistry.counter(
                                "backend_ai_ingest_monitor_terminal_total",
                                "status", statusLabel)
                        .increment());
        recordSafely("terminal_duration", () -> Timer.builder(
                        "backend_ai_ingest_orchestration_duration_seconds")
                .description("Backend-observed duration of durable AI ingestion orchestration")
                .publishPercentileHistogram()
                .tag("status", statusLabel)
                .register(meterRegistry)
                .record(safeElapsed));
        if (!counted) {
            releaseObservation(claim);
        }
        return counted;
    }

    public void recordCacheInvalidation(boolean success) {
        recordSafely("cache_invalidation", () -> meterRegistry.counter(
                                "backend_ai_ingest_cache_invalidations_total",
                                "result", success ? "success" : "failure")
                        .increment());
    }

    public void recordManualDataRequired(UUID runId) {
        ObservationClaim claim = claimObservation("manual_data_required", runId);
        if (claim == null) {
            return;
        }
        boolean counted = recordSafely(
                "manual_data_required",
                () -> meterRegistry.counter("backend_ai_ingest_manual_data_required_total")
                        .increment());
        if (!counted) {
            releaseObservation(claim);
        }
    }

    private String normalizeTerminalStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        return TERMINAL_STATUSES.contains(normalized) ? normalized : "FAILED";
    }

    private Duration nonNegative(Duration duration) {
        return duration == null || duration.isNegative() ? Duration.ZERO : duration;
    }

    private ObservationClaim claimObservation(String category, UUID runId) {
        if (runId == null) {
            return null;
        }
        String localKey = category + ":" + runId;
        if (redisTemplate != null) {
            try {
                Boolean claimed = redisTemplate.opsForValue().setIfAbsent(
                        OBSERVATION_KEY_PREFIX + localKey,
                        "1",
                        OBSERVATION_TTL);
                if (Boolean.TRUE.equals(claimed)) {
                    observedLocalKeys.put(localKey, Boolean.TRUE);
                    return new ObservationClaim(localKey, true);
                }
                if (Boolean.FALSE.equals(claimed)) {
                    return null;
                }
            } catch (RuntimeException exception) {
                log.warn(
                        "AI ingestion metric dedupe failed operation=claim errorType={}",
                        exception.getClass().getSimpleName());
            }
        }
        if (observedLocalKeys.asMap().putIfAbsent(localKey, Boolean.TRUE) != null) {
            return null;
        }
        return new ObservationClaim(localKey, false);
    }

    private void releaseObservation(ObservationClaim claim) {
        observedLocalKeys.invalidate(claim.localKey());
        if (!claim.redisBacked() || redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.delete(OBSERVATION_KEY_PREFIX + claim.localKey());
        } catch (RuntimeException exception) {
            log.warn(
                    "AI ingestion metric dedupe failed operation=release errorType={}",
                    exception.getClass().getSimpleName());
        }
    }

    private boolean recordSafely(String operation, Runnable recorder) {
        try {
            recorder.run();
            return true;
        } catch (RuntimeException exception) {
            log.warn(
                    "AI ingestion metric recording failed operation={} errorType={}",
                    operation,
                    exception.getClass().getSimpleName());
            return false;
        }
    }

    private record ObservationClaim(String localKey, boolean redisBacked) {}
}
