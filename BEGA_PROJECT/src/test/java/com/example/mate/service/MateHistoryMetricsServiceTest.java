package com.example.mate.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class MateHistoryMetricsServiceTest {

    @Test
    void recordRequest_recordsSuccessAndFailureByNormalizedGroup() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MateHistoryMetricsService metricsService = new MateHistoryMetricsService(meterRegistry);

        metricsService.recordRequest("ALL", "success", 1_000_000L);
        metricsService.recordRequest("archived", "failure", 2_000_000L);

        assertThat(timer(meterRegistry, "all", "success").count()).isEqualTo(1L);
        assertThat(timer(meterRegistry, "invalid", "failure").count()).isEqualTo(1L);
    }

    @Test
    void recordRequest_usesUnknownForMissingGroupAndUnexpectedResult() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MateHistoryMetricsService metricsService = new MateHistoryMetricsService(meterRegistry);

        metricsService.recordRequest(null, "retry", 1_000_000L);

        assertThat(timer(meterRegistry, "unknown", "unknown").count()).isEqualTo(1L);
    }

    @Test
    void recordRequest_ignoresNegativeDuration() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MateHistoryMetricsService metricsService = new MateHistoryMetricsService(meterRegistry);

        metricsService.recordRequest("ongoing", "success", -1L);

        assertThat(meterRegistry.find(MateHistoryMetricsService.REQUEST_DURATION_METRIC).timer()).isNull();
    }

    private Timer timer(SimpleMeterRegistry meterRegistry, String group, String result) {
        return meterRegistry.get(MateHistoryMetricsService.REQUEST_DURATION_METRIC)
                .tag("group", group)
                .tag("result", result)
                .timer();
    }
}
