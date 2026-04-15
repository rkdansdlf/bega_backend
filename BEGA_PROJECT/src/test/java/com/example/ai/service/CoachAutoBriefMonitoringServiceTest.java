package com.example.ai.service;

import com.example.ai.service.AiProxyService.ProxyByteResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class CoachAutoBriefMonitoringServiceTest {

    @Test
    void recordCoachAnalyzeDuration_tracksAutoBriefLatencySeparately() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CoachAutoBriefMonitoringService monitoringService = new CoachAutoBriefMonitoringService(
                mock(AiProxyService.class),
                meterRegistry,
                new ObjectMapper(),
                true);

        monitoringService.recordCoachAnalyzeDuration("auto_brief", 200, Duration.ofMillis(850).toNanos());

        Timer timer = meterRegistry.find("coach_brief_request_duration_seconds")
                .tags("request_mode", "auto_brief", "status_group", "2xx")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1L);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(850.0d);
    }

    @Test
    void refreshHealthSnapshots_populatesSummaryAndBreakdownGauges() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AiProxyService aiProxyService = mock(AiProxyService.class);
        CoachAutoBriefMonitoringService monitoringService = new CoachAutoBriefMonitoringService(
                aiProxyService,
                meterRegistry,
                new ObjectMapper(),
                true);

        given(aiProxyService.forwardGet(eq("/ai/coach/auto-brief/ops/health?window=today")))
                .willReturn(jsonResponse("""
                        {
                          "generated_at_utc": "2026-04-08T00:15:00Z",
                          "summary": {
                            "loaded_target_count": 12,
                            "selected_target_count": 6,
                            "generated_success_count": 2,
                            "cache_hit_count": 3,
                            "in_progress_count": 1,
                            "failed_count": 1,
                            "unresolved_count": 3,
                            "completed_count": 3,
                            "cache_state_breakdown": {
                              "COMPLETED": 3,
                              "FAILED_LOCKED": 1,
                              "PENDING_WAIT": 1,
                              "MISSING": 1
                            },
                            "data_quality_breakdown": {
                              "grounded": 2,
                              "partial": 2,
                              "insufficient": 1,
                              "unknown": 1
                            }
                          }
                        }
                        """));
        given(aiProxyService.forwardGet(eq("/ai/coach/auto-brief/ops/health?window=tomorrow")))
                .willReturn(jsonResponse("""
                        {
                          "generated_at_utc": "2026-04-08T00:16:00Z",
                          "summary": {
                            "loaded_target_count": 8,
                            "selected_target_count": 4,
                            "generated_success_count": 1,
                            "cache_hit_count": 2,
                            "in_progress_count": 0,
                            "failed_count": 0,
                            "unresolved_count": 1,
                            "completed_count": 3,
                            "cache_state_breakdown": {
                              "COMPLETED": 3,
                              "FAILED_LOCKED": 0,
                              "PENDING_WAIT": 0,
                              "UNKNOWN": 1
                            },
                            "data_quality_breakdown": {
                              "grounded": 1,
                              "partial": 1,
                              "insufficient": 0,
                              "unknown": 2
                            }
                          }
                        }
                        """));

        monitoringService.refreshHealthSnapshots(List.of("today", "tomorrow"));

        assertThat(gaugeValue(meterRegistry, "coach_auto_brief_health_summary", "window", "today", "metric", "unresolved_count"))
                .isEqualTo(3.0d);
        assertThat(gaugeValue(meterRegistry, "coach_auto_brief_health_summary", "window", "today", "metric", "cache_hit_count"))
                .isEqualTo(3.0d);
        assertThat(gaugeValue(meterRegistry, "coach_auto_brief_health_cache_state", "window", "today", "cache_state", "FAILED_LOCKED"))
                .isEqualTo(1.0d);
        assertThat(gaugeValue(meterRegistry, "coach_auto_brief_health_cache_state", "window", "today", "cache_state", "PENDING_WAIT"))
                .isEqualTo(1.0d);
        assertThat(gaugeValue(meterRegistry, "coach_auto_brief_health_data_quality", "window", "today", "data_quality", "insufficient"))
                .isEqualTo(1.0d);
        assertThat(gaugeValue(meterRegistry, "coach_auto_brief_health_last_refresh_timestamp_seconds", "window", "today"))
                .isEqualTo(OffsetDateTime.parse("2026-04-08T00:15:00Z").toEpochSecond());

        Counter successCounter = meterRegistry.find("coach_auto_brief_health_poll_total")
                .tags("window", "today", "result", "success")
                .counter();
        assertThat(successCounter).isNotNull();
        assertThat(successCounter.count()).isEqualTo(1.0d);
    }

    @Test
    void refreshHealthSnapshots_recordsPollFailures() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AiProxyService aiProxyService = mock(AiProxyService.class);
        CoachAutoBriefMonitoringService monitoringService = new CoachAutoBriefMonitoringService(
                aiProxyService,
                meterRegistry,
                new ObjectMapper(),
                true);

        given(aiProxyService.forwardGet(eq("/ai/coach/auto-brief/ops/health?window=today")))
                .willReturn(new ProxyByteResponse(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        new HttpHeaders(),
                        "{\"success\":false}".getBytes(StandardCharsets.UTF_8)));

        monitoringService.refreshHealthSnapshots(List.of("today"));

        Counter failureCounter = meterRegistry.find("coach_auto_brief_health_poll_total")
                .tags("window", "today", "result", "failure")
                .counter();

        assertThat(failureCounter).isNotNull();
        assertThat(failureCounter.count()).isEqualTo(1.0d);
        assertThat(meterRegistry.find("coach_auto_brief_health_summary")
                .tags("window", "today", "metric", "unresolved_count")
                .gauge()).isNull();
    }

    private ProxyByteResponse jsonResponse(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json");
        return new ProxyByteResponse(
                HttpStatus.OK,
                headers,
                body.getBytes(StandardCharsets.UTF_8));
    }

    private double gaugeValue(SimpleMeterRegistry meterRegistry, String name, String... tags) {
        Gauge gauge = meterRegistry.find(name).tags(tags).gauge();
        assertThat(gauge).isNotNull();
        return gauge.value();
    }
}
