package com.example.mate.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class MateHistoryMetricsService {

    static final String REQUEST_DURATION_METRIC = "mate_history_request_duration_seconds";

    private final MeterRegistry meterRegistry;

    public MateHistoryMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordRequest(String group, String result, long durationNanos) {
        if (durationNanos < 0) {
            return;
        }

        Timer.builder(REQUEST_DURATION_METRIC)
                .description("Mate history request duration")
                .publishPercentileHistogram()
                .tags(
                        "group", normalizeGroup(group),
                        "result", normalizeResult(result))
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    static String normalizeGroup(String group) {
        if (group == null || group.isBlank()) {
            return "unknown";
        }

        String normalized = group.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "all", "completed", "ongoing" -> normalized;
            default -> "invalid";
        };
    }

    private String normalizeResult(String result) {
        if ("success".equals(result) || "failure".equals(result)) {
            return result;
        }
        return "unknown";
    }
}
