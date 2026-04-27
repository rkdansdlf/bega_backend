package com.example.ai.service;

import com.example.ai.service.AiProxyService.ProxyByteResponse;
import com.example.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class CoachAutoBriefMonitoringService {

    private static final List<String> DEFAULT_WINDOWS = List.of("today", "tomorrow");
    private static final List<String> SUMMARY_METRICS = List.of(
            "loaded_target_count",
            "selected_target_count",
            "generated_success_count",
            "cache_hit_count",
            "in_progress_count",
            "failed_count",
            "unresolved_count",
            "completed_count");
    private static final List<String> DEFAULT_CACHE_STATES = List.of(
            "COMPLETED",
            "PENDING",
            "PENDING_WAIT",
            "FAILED_LOCKED",
            "FAILED",
            "MISSING",
            "UNAVAILABLE",
            "UNKNOWN");
    private static final List<String> DEFAULT_DATA_QUALITIES = List.of(
            "grounded",
            "partial",
            "insufficient",
            "unknown");

    private final AiProxyService aiProxyService;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final boolean monitoringEnabled;
    private final Map<String, AtomicLong> gaugeValues = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> knownCacheStatesByWindow = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> knownDataQualitiesByWindow = new ConcurrentHashMap<>();

    public CoachAutoBriefMonitoringService(
            AiProxyService aiProxyService,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper,
            @Value("${app.ai.coach-auto-brief.monitoring.enabled:true}") boolean monitoringEnabled) {
        this.aiProxyService = aiProxyService;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
        this.monitoringEnabled = monitoringEnabled;
    }

    public String extractRequestMode(String payload) {
        if (!StringUtils.hasText(payload)) {
            return "unknown";
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            return normalizeRequestMode(root.path("request_mode").asText(""));
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    public void recordCoachAnalyzeDuration(String requestMode, int statusCode, long durationNanos) {
        if (!monitoringEnabled || durationNanos < 0L) {
            return;
        }

        Timer.builder("coach_brief_request_duration_seconds")
                .description("Coach analyze proxy duration grouped by request mode")
                .publishPercentileHistogram()
                .tags(
                        "request_mode", normalizeRequestMode(requestMode),
                        "status_group", normalizeStatusGroup(statusCode))
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public int resolveStatusCode(Throwable error) {
        if (error instanceof BusinessException businessException) {
            return businessException.getStatus().value();
        }
        return 500;
    }

    @Scheduled(
            fixedRateString = "${app.ai.coach-auto-brief.monitoring.poll-interval-ms:60000}",
            initialDelayString = "${app.ai.coach-auto-brief.monitoring.initial-delay-ms:15000}")
    public void refreshHealthSnapshots() {
        refreshHealthSnapshots(DEFAULT_WINDOWS);
    }

    void refreshHealthSnapshots(List<String> windows) {
        if (!monitoringEnabled) {
            return;
        }

        for (String window : windows) {
            refreshWindow(window);
        }
    }

    private void refreshWindow(String window) {
        long startNanos = System.nanoTime();
        String outcome = "failure";

        try {
            ProxyByteResponse response = aiProxyService.forwardGet("/ai/coach/auto-brief/ops/health?window=" + window);
            if (!response.status().is2xxSuccessful()) {
                log.warn("Coach auto brief monitoring poll failed window={} status={}", window, response.status().value());
                return;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode summary = root.path("summary");
            updateSummary(window, summary);
            updateBreakdown(
                    "coach_auto_brief_health_cache_state",
                    "cache_state",
                    window,
                    summary.path("cache_state_breakdown"),
                    DEFAULT_CACHE_STATES,
                    knownCacheStatesByWindow);
            updateBreakdown(
                    "coach_auto_brief_health_data_quality",
                    "data_quality",
                    window,
                    summary.path("data_quality_breakdown"),
                    DEFAULT_DATA_QUALITIES,
                    knownDataQualitiesByWindow);
            setGauge(
                    "coach_auto_brief_health_last_refresh_timestamp_seconds",
                    Tags.of("window", window),
                    extractRefreshEpochSeconds(root));
            outcome = "success";
        } catch (Exception exception) {
            log.warn("Coach auto brief monitoring poll failed window={} error={}", window, exception.toString());
        } finally {
            meterRegistry.counter(
                            "coach_auto_brief_health_poll_total",
                            "window", window,
                            "result", outcome)
                    .increment();
            Timer.builder("coach_auto_brief_health_poll_duration_seconds")
                    .description("Coach auto brief health poll duration")
                    .publishPercentileHistogram()
                    .tags("window", window, "result", outcome)
                    .register(meterRegistry)
                    .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
        }
    }

    private void updateSummary(String window, JsonNode summary) {
        for (String metric : SUMMARY_METRICS) {
            setGauge(
                    "coach_auto_brief_health_summary",
                    Tags.of("window", window, "metric", metric),
                    summary.path(metric).asLong(0L));
        }
    }

    private void updateBreakdown(
            String metricName,
            String tagName,
            String window,
            JsonNode breakdown,
            List<String> defaultCategories,
            Map<String, Set<String>> knownCategoriesByWindow) {
        Set<String> categories = knownCategoriesByWindow.computeIfAbsent(window, ignored -> new LinkedHashSet<>(defaultCategories));
        List<String> nextCategories = new ArrayList<>(categories);

        if (breakdown.isObject()) {
            breakdown.fieldNames().forEachRemaining(category -> {
                if (categories.add(category)) {
                    nextCategories.add(category);
                }
            });
        }

        for (String category : nextCategories) {
            setGauge(
                    metricName,
                    Tags.of("window", window, tagName, category),
                    breakdown.path(category).asLong(0L));
        }
    }

    private void setGauge(String metricName, Tags tags, long value) {
        atomicGauge(metricName, tags).set(value);
    }

    private AtomicLong atomicGauge(String metricName, Tags tags) {
        String key = buildGaugeKey(metricName, tags);
        return gaugeValues.computeIfAbsent(key, ignored -> {
            AtomicLong holder = new AtomicLong(0L);
            Gauge.builder(metricName, holder, AtomicLong::doubleValue)
                    .tags(tags)
                    .register(meterRegistry);
            return holder;
        });
    }

    private String buildGaugeKey(String metricName, Tags tags) {
        StringBuilder builder = new StringBuilder(metricName);
        tags.stream()
                .forEach(tag -> builder.append('|')
                        .append(tag.getKey())
                        .append('=')
                        .append(tag.getValue()));
        return builder.toString();
    }

    private long extractRefreshEpochSeconds(JsonNode root) {
        String rawGeneratedAt = root.path("generated_at_utc").asText("");
        if (!StringUtils.hasText(rawGeneratedAt)) {
            return System.currentTimeMillis() / 1000L;
        }

        try {
            return OffsetDateTime.parse(rawGeneratedAt).toEpochSecond();
        } catch (DateTimeParseException ignored) {
            return System.currentTimeMillis() / 1000L;
        }
    }

    private String normalizeRequestMode(String requestMode) {
        String normalized = String.valueOf(requestMode).trim().toLowerCase(Locale.ROOT);
        if ("auto_brief".equals(normalized) || "manual_detail".equals(normalized)) {
            return normalized;
        }
        return "unknown";
    }

    private String normalizeStatusGroup(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            return "2xx";
        }
        if (statusCode >= 400 && statusCode < 500) {
            return "4xx";
        }
        if (statusCode >= 500) {
            return "5xx";
        }
        return "other";
    }
}
