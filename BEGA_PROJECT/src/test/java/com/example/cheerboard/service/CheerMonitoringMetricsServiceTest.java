package com.example.cheerboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CheerMonitoringMetricsServiceTest {

    @Test
    void recordPostChangesPolling_recordsDurationAndCountsWithStableTags() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CheerMonitoringMetricsService metricsService = new CheerMonitoringMetricsService(meterRegistry);

        metricsService.recordPostChangesPolling(true, 200, 12, TimeUnit.MILLISECONDS.toNanos(25), "success");

        Timer duration = meterRegistry.get("cheer_post_changes_duration_seconds")
                .tag("team_scoped", "true")
                .tag("result", "success")
                .timer();
        DistributionSummary scanCount = meterRegistry.get("cheer_post_changes_scan_count")
                .tag("team_scoped", "true")
                .tag("result", "success")
                .summary();
        DistributionSummary visibleCount = meterRegistry.get("cheer_post_changes_visible_count")
                .tag("team_scoped", "true")
                .tag("result", "success")
                .summary();

        assertThat(duration.count()).isEqualTo(1L);
        assertThat(duration.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(25.0d);
        assertThat(scanCount.totalAmount()).isEqualTo(200.0d);
        assertThat(visibleCount.totalAmount()).isEqualTo(12.0d);
    }

    @Test
    void recordFeedRequest_recordsDurationWithBoundedTags() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CheerMonitoringMetricsService metricsService = new CheerMonitoringMetricsService(meterRegistry);

        metricsService.recordFeedRequest(
                "feed",
                true,
                "NOTICE",
                null,
                true,
                20,
                "success",
                TimeUnit.MILLISECONDS.toNanos(30));

        Timer duration = meterRegistry.get(CheerMonitoringMetricsService.FEED_REQUEST_DURATION_METRIC)
                .tag("endpoint", "feed")
                .tag("team_scoped", "true")
                .tag("post_type", "notice")
                .tag("algorithm", "none")
                .tag("authenticated", "true")
                .tag("size_bucket", "1_20")
                .tag("result", "success")
                .timer();

        assertThat(duration.count()).isEqualTo(1L);
        assertThat(duration.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(30.0d);
    }

    @Test
    void recordFeedRequest_usesFallbackTagsAndIgnoresNegativeDuration() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CheerMonitoringMetricsService metricsService = new CheerMonitoringMetricsService(meterRegistry);

        metricsService.recordFeedRequest(
                "unexpected",
                false,
                "poll",
                "unknown",
                false,
                0,
                "retry",
                TimeUnit.MILLISECONDS.toNanos(10));
        metricsService.recordFeedRequest("feed", false, null, null, false, 20, "success", -1);

        Timer duration = meterRegistry.get(CheerMonitoringMetricsService.FEED_REQUEST_DURATION_METRIC)
                .tag("endpoint", "unknown")
                .tag("team_scoped", "false")
                .tag("post_type", "invalid")
                .tag("algorithm", "hybrid")
                .tag("authenticated", "false")
                .tag("size_bucket", "invalid")
                .tag("result", "unknown")
                .timer();

        assertThat(duration.count()).isEqualTo(1L);
        assertThat(meterRegistry.find(CheerMonitoringMetricsService.FEED_REQUEST_DURATION_METRIC)
                .tag("endpoint", "feed")
                .timer()).isNull();
    }

    @Test
    void registerFeedEnrichmentBulkheadMetrics_recordsActiveAndLimitGauges() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CheerMonitoringMetricsService metricsService = new CheerMonitoringMetricsService(meterRegistry);
        BulkheadState state = new BulkheadState();
        state.active.set(3);
        state.limit.set(8);

        metricsService.registerFeedEnrichmentBulkheadMetrics(
                state,
                value -> value.active.get(),
                value -> value.limit.get());

        Gauge active = meterRegistry.get("cheer_feed_enrichment_bulkhead_active")
                .tag("bulkhead", "feed_enrichment")
                .gauge();
        Gauge limit = meterRegistry.get("cheer_feed_enrichment_bulkhead_limit")
                .tag("bulkhead", "feed_enrichment")
                .gauge();

        assertThat(active.value()).isEqualTo(3.0d);
        assertThat(limit.value()).isEqualTo(8.0d);
    }

    @Test
    void recordFeedEnrichment_recordsOnlyBoundedResults() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CheerMonitoringMetricsService metricsService = new CheerMonitoringMetricsService(meterRegistry);

        metricsService.recordFeedEnrichment("success");
        metricsService.recordFeedEnrichment("timeout");
        metricsService.recordFeedEnrichment("busy");
        metricsService.recordFeedEnrichment("unexpected");

        assertEnrichmentCount(meterRegistry, "success", 1.0d);
        assertEnrichmentCount(meterRegistry, "timeout", 1.0d);
        assertEnrichmentCount(meterRegistry, "busy", 1.0d);
        assertEnrichmentCount(meterRegistry, "failure", 1.0d);
        assertThat(meterRegistry.find(CheerMonitoringMetricsService.FEED_ENRICHMENT_EVENT_METRIC)
                .tag("result", "unexpected")
                .counter()).isNull();
    }

    private void assertEnrichmentCount(SimpleMeterRegistry meterRegistry, String result, double expected) {
        Counter counter = meterRegistry.get(CheerMonitoringMetricsService.FEED_ENRICHMENT_EVENT_METRIC)
                .tag("result", result)
                .counter();
        assertThat(counter.count()).isEqualTo(expected);
    }

    private static final class BulkheadState {
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger limit = new AtomicInteger();
    }
}
