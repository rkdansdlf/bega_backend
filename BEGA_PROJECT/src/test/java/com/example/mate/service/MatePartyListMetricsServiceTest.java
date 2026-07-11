package com.example.mate.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MatePartyListMetricsServiceTest {

    @Test
    void recordRequest_recordsNormalizedFilterAndSortTags() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MatePartyListMetricsService metricsService = new MatePartyListMetricsService(meterRegistry);

        metricsService.recordRequest(
                "KIA",
                "Jamsil",
                LocalDate.of(2026, 7, 10),
                "seat",
                "PENDING",
                "gameDate",
                "asc",
                30,
                true,
                "success",
                1_000_000L);

        Timer timer = meterRegistry.get(MatePartyListMetricsService.REQUEST_DURATION_METRIC)
                .tag("team_filter", "present")
                .tag("stadium_filter", "present")
                .tag("date_filter", "present")
                .tag("search_filter", "present")
                .tag("status", "pending")
                .tag("sort", "gamedate")
                .tag("sort_dir", "asc")
                .tag("size_bucket", "21_30")
                .tag("authenticated", "true")
                .tag("result", "success")
                .timer();

        assertThat(timer.count()).isEqualTo(1L);
    }

    @Test
    void recordRequest_usesSafeLowCardinalityFallbackTags() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MatePartyListMetricsService metricsService = new MatePartyListMetricsService(meterRegistry);

        metricsService.recordRequest(
                null,
                " ",
                null,
                null,
                "unexpected",
                "description",
                "sideways",
                -1,
                false,
                "retry",
                1_000_000L);

        Timer timer = meterRegistry.get(MatePartyListMetricsService.REQUEST_DURATION_METRIC)
                .tag("team_filter", "absent")
                .tag("stadium_filter", "absent")
                .tag("date_filter", "absent")
                .tag("search_filter", "absent")
                .tag("status", "invalid")
                .tag("sort", "createdat")
                .tag("sort_dir", "desc")
                .tag("size_bucket", "invalid")
                .tag("authenticated", "false")
                .tag("result", "unknown")
                .timer();

        assertThat(timer.count()).isEqualTo(1L);
    }

    @Test
    void recordRequest_ignoresNegativeDuration() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MatePartyListMetricsService metricsService = new MatePartyListMetricsService(meterRegistry);

        metricsService.recordRequest(
                null,
                null,
                null,
                null,
                null,
                "createdAt",
                "desc",
                9,
                false,
                "success",
                -1L);

        assertThat(meterRegistry.find(MatePartyListMetricsService.REQUEST_DURATION_METRIC).timer()).isNull();
    }
}
