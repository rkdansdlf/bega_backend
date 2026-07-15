package com.example.ai.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class AiIngestMonitoringMetricsTest {

    @Test
    void registryFailureNeverEscapesIntoOrchestrationFlow() {
        MeterRegistry meterRegistry = org.mockito.Mockito.mock(MeterRegistry.class);
        when(meterRegistry.counter(anyString(), any(String[].class)))
                .thenThrow(new IllegalStateException("registry unavailable"));
        AiIngestMonitoringMetrics metrics = new AiIngestMonitoringMetrics(meterRegistry);

        assertThatCode(() -> metrics.recordSubmissionAccepted(false))
                .doesNotThrowAnyException();
        assertThatCode(() -> metrics.recordTerminal(
                        UUID.fromString("99999999-9999-4999-8999-999999999999"),
                        "SUCCEEDED",
                        Duration.ofSeconds(1)))
                .doesNotThrowAnyException();
    }

    @Test
    void redisClaimDeduplicatesAcrossServiceRestartAndAllowsStatusTransition() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
        Set<String> claimedKeys = ConcurrentHashMap.newKeySet();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), eq(Duration.ofDays(7))))
                .thenAnswer(invocation -> claimedKeys.add(invocation.getArgument(0)));
        AiIngestMonitoringMetrics firstProcess = new AiIngestMonitoringMetrics(
                meterRegistry,
                redisTemplate);
        AiIngestMonitoringMetrics restartedProcess = new AiIngestMonitoringMetrics(
                meterRegistry,
                redisTemplate);
        UUID runId = UUID.fromString("88888888-8888-4888-8888-888888888888");

        assertThat(firstProcess.recordTerminal(runId, "MONITOR_TIMEOUT", Duration.ofHours(2)))
                .isTrue();
        assertThat(restartedProcess.recordTerminal(runId, "MONITOR_TIMEOUT", Duration.ofHours(2)))
                .isFalse();
        assertThat(restartedProcess.recordTerminal(runId, "SUCCEEDED", Duration.ofHours(2)))
                .isTrue();

        assertThat(meterRegistry.get("backend_ai_ingest_monitor_terminal_total")
                .tag("status", "MONITOR_TIMEOUT")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("backend_ai_ingest_monitor_terminal_total")
                .tag("status", "SUCCEEDED")
                .counter()
                .count()).isEqualTo(1.0d);

        firstProcess.recordManualDataRequired(runId);
        restartedProcess.recordManualDataRequired(runId);
        assertThat(meterRegistry.get("backend_ai_ingest_manual_data_required_total")
                .counter()
                .count()).isEqualTo(1.0d);
    }
}
