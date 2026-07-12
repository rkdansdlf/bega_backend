package com.example.ai.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class AiProxyMonitoringMetricsService {

    static final String UPSTREAM_REQUEST_DURATION_METRIC = "ai_proxy_upstream_request_duration_seconds";

    private static final String UNKNOWN = "unknown";
    private static final Map<String, String> KNOWN_ENDPOINTS = Map.ofEntries(
            Map.entry("/ai/chat/completion", "chat_completion"),
            Map.entry("/ai/chat/stream", "chat_stream"),
            Map.entry("/ai/chat/voice", "chat_voice"),
            Map.entry("/ai/coach/analyze", "coach_analyze"),
            Map.entry("/ai/coach/auto-brief/ops/health", "coach_auto_brief_health"),
            Map.entry("/ai/release-decision/presets", "release_decision_presets"),
            Map.entry("/ai/release-decision/eval-cases", "release_decision_eval_cases"),
            Map.entry("/ai/release-decision/artifacts", "release_decision_artifacts"),
            Map.entry("/ai/release-decision/draft", "release_decision_draft"),
            Map.entry("/ai/release-decision/evaluate", "release_decision_evaluate"),
            Map.entry("/ai/release-decision/save", "release_decision_save"));

    private final MeterRegistry meterRegistry;

    public AiProxyMonitoringMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    static AiProxyMonitoringMetricsService noop() {
        return new AiProxyMonitoringMetricsService(null);
    }

    public void recordUpstreamRequest(
            String uri,
            String mode,
            Integer statusCode,
            String result,
            long durationNanos) {
        if (meterRegistry == null || durationNanos < 0) {
            return;
        }
        Timer.builder(UPSTREAM_REQUEST_DURATION_METRIC)
                .description("Backend AI proxy upstream request/header wait duration")
                .tag("endpoint", normalizeEndpoint(uri))
                .tag("mode", normalizeMode(mode))
                .tag("status", normalizeStatus(statusCode))
                .tag("result", normalizeResult(result))
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private String normalizeEndpoint(String uri) {
        if (uri == null || uri.isBlank()) {
            return UNKNOWN;
        }
        String normalized = uri.trim();
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        String exact = KNOWN_ENDPOINTS.get(normalized);
        if (exact != null) {
            return exact;
        }
        if (normalized.startsWith("/ai/release-decision/artifacts/")) {
            return "release_decision_artifact_detail";
        }
        return UNKNOWN;
    }

    private String normalizeMode(String mode) {
        if ("byte".equals(mode) || "stream_header".equals(mode)) {
            return mode;
        }
        return UNKNOWN;
    }

    private String normalizeStatus(Integer statusCode) {
        if (statusCode == null) {
            return "none";
        }
        int family = statusCode / 100;
        return switch (family) {
            case 1, 2, 3, 4, 5 -> family + "xx";
            default -> "other";
        };
    }

    private String normalizeResult(String result) {
        if (result == null || result.isBlank()) {
            return "failure";
        }
        String normalized = result.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "_");
        return switch (normalized) {
            case "success", "upstream_error", "connection_failure", "timeout", "failure" -> normalized;
            default -> "failure";
        };
    }
}
