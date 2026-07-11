package com.example.cheerboard.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CheerMonitoringMetricsService {

    static final String FEED_REQUEST_DURATION_METRIC = "cheer_feed_request_duration_seconds";

    private static final Set<String> FEED_ENDPOINTS = Set.of("feed", "feed_lightweight", "search", "hot");
    private static final Set<String> POST_TYPES = Set.of("normal", "notice");
    private static final Set<String> HOT_ALGORITHMS = Set.of("time_decay", "engagement_rate", "hybrid");

    private final MeterRegistry meterRegistry;

    public <T> void registerFeedEnrichmentBulkheadMetrics(
            T state,
            ToDoubleFunction<T> activeSupplier,
            ToDoubleFunction<T> limitSupplier) {
        Gauge.builder("cheer_feed_enrichment_bulkhead_active", state, activeSupplier)
                .description("Active cheer feed enrichment bulkhead permits")
                .tag("bulkhead", "feed_enrichment")
                .register(meterRegistry);
        Gauge.builder("cheer_feed_enrichment_bulkhead_limit", state, limitSupplier)
                .description("Configured cheer feed enrichment bulkhead permits")
                .tag("bulkhead", "feed_enrichment")
                .register(meterRegistry);
    }

    public void recordBattleVote(String result) {
        Counter.builder("cheer_battle_vote_total")
                .description("응원 배틀 투표 처리 결과 건수")
                .tag("result", normalizeTag(result))
                .register(meterRegistry)
                .increment();
    }

    public void recordWebSocketEvent(String event) {
        Counter.builder("cheer_websocket_events_total")
                .description("응원 배틀 웹소켓 이벤트 건수")
                .tag("event", normalizeTag(event))
                .register(meterRegistry)
                .increment();
    }

    public void recordPostChangesPolling(
            boolean teamScoped,
            int scannedCount,
            int visibleCount,
            long durationNanos,
            String result) {
        String teamScopedTag = Boolean.toString(teamScoped);
        String resultTag = normalizeTag(result);

        if (durationNanos >= 0) {
            Timer.builder("cheer_post_changes_duration_seconds")
                    .description("Cheer post changes polling duration")
                    .publishPercentileHistogram()
                    .tags("team_scoped", teamScopedTag, "result", resultTag)
                    .register(meterRegistry)
                    .record(durationNanos, TimeUnit.NANOSECONDS);
        }

        DistributionSummary.builder("cheer_post_changes_scan_count")
                .description("Cheer post changes polling scanned post count")
                .tags("team_scoped", teamScopedTag, "result", resultTag)
                .register(meterRegistry)
                .record(Math.max(0, scannedCount));

        DistributionSummary.builder("cheer_post_changes_visible_count")
                .description("Cheer post changes polling visible post count")
                .tags("team_scoped", teamScopedTag, "result", resultTag)
                .register(meterRegistry)
                .record(Math.max(0, visibleCount));
    }

    public void recordFeedRequest(
            String endpoint,
            boolean teamScoped,
            String postType,
            String algorithm,
            boolean authenticated,
            int pageSize,
            String result,
            long durationNanos) {
        if (durationNanos < 0) {
            return;
        }

        Timer.builder(FEED_REQUEST_DURATION_METRIC)
                .description("Cheer feed request duration")
                .publishPercentileHistogram()
                .tags(
                        "endpoint", normalizeEndpoint(endpoint),
                        "team_scoped", Boolean.toString(teamScoped),
                        "post_type", normalizePostType(postType),
                        "algorithm", normalizeAlgorithm(algorithm),
                        "authenticated", Boolean.toString(authenticated),
                        "size_bucket", pageSizeBucket(pageSize),
                        "result", normalizeResult(result))
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private String normalizeEndpoint(String endpoint) {
        String normalized = normalizeTag(endpoint);
        return FEED_ENDPOINTS.contains(normalized) ? normalized : "unknown";
    }

    private String normalizePostType(String postType) {
        if (postType == null || postType.isBlank()) {
            return "all";
        }
        String normalized = postType.trim().toLowerCase(Locale.ROOT);
        return POST_TYPES.contains(normalized) ? normalized : "invalid";
    }

    private String normalizeAlgorithm(String algorithm) {
        if (algorithm == null || algorithm.isBlank()) {
            return "none";
        }
        String normalized = algorithm.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_');
        return HOT_ALGORITHMS.contains(normalized) ? normalized : "hybrid";
    }

    private String pageSizeBucket(int pageSize) {
        if (pageSize <= 0) {
            return "invalid";
        }
        if (pageSize <= 20) {
            return "1_20";
        }
        if (pageSize <= 50) {
            return "21_50";
        }
        return "over_50";
    }

    private String normalizeResult(String result) {
        if ("success".equals(result) || "failure".equals(result)) {
            return result;
        }
        return "unknown";
    }

    private String normalizeTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase();
    }
}
