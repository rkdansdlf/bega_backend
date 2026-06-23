package com.example.prediction;

import java.util.Locale;

import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Service
public class PredictionLiveMetricsService {

    private static final String SNAPSHOT_METRIC = "prediction_live_snapshot_total";
    private static final String RELAY_METRIC = "prediction_live_relay_snapshot_total";
    private static final String MANUAL_REQUIRED_METRIC = "prediction_live_manual_required_total";

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

    private String normalizeTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-]", "_");
    }
}
