package com.example.prediction;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Service
public class PredictionLiveMetricsService {

    private static final String SNAPSHOT_METRIC = "prediction_live_snapshot_total";
    private static final String RELAY_METRIC = "prediction_live_relay_snapshot_total";
    private static final String MANUAL_REQUIRED_METRIC = "prediction_live_manual_required_total";
    private static final String REQUEST_DURATION_METRIC = "prediction_live_request_duration_seconds";

    private final MeterRegistry meterRegistry;

    public PredictionLiveMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordLiveSnapshot(String scoreSource, boolean inningScoresPresent) {
        Counter.builder(SNAPSHOT_METRIC)
                .description("Prediction live snapshot requests")
                .tag("score_source", normalizeTag(scoreSource))
                .tag("inning_scores", inningScoresPresent ? "present" : "absent")
                .register(meterRegistry)
                .increment();
    }

    public void recordLiveRelaySnapshot(String result) {
        Counter.builder(RELAY_METRIC)
                .description("Prediction live relay snapshot requests")
                .tag("result", normalizeTag(result))
                .register(meterRegistry)
                .increment();
    }

    public void recordManualRequired(String channel) {
        Counter.builder(MANUAL_REQUIRED_METRIC)
                .description("Prediction live polling manual data required results")
                .tag("channel", normalizeTag(channel))
                .register(meterRegistry)
                .increment();
    }

    public void recordRequestDuration(
            String endpoint,
            String result,
            int statusCode,
            int batchSize,
            long durationNanos) {
        if (durationNanos < 0) {
            return;
        }

        Timer.builder(REQUEST_DURATION_METRIC)
                .description("Prediction live polling request duration")
                .publishPercentileHistogram()
                .tags(
                        "endpoint", normalizeTag(endpoint),
                        "result", normalizeTag(result),
                        "status_group", normalizeStatusGroup(statusCode),
                        "batch_size", normalizeBatchSize(batchSize))
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private String normalizeTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-]", "_");
    }

    private String normalizeStatusGroup(int statusCode) {
        if (statusCode < 100) {
            return "unknown";
        }
        return (statusCode / 100) + "xx";
    }

    private String normalizeBatchSize(int batchSize) {
        if (batchSize <= 0) {
            return "empty";
        }
        if (batchSize == 1) {
            return "single";
        }
        if (batchSize <= 5) {
            return "2_5";
        }
        if (batchSize <= 20) {
            return "6_20";
        }
        if (batchSize <= 50) {
            return "21_50";
        }
        return "over_limit";
    }
}
