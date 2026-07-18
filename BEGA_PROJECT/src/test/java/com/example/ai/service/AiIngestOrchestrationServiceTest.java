package com.example.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ai.config.AiIngestProperties;
import com.example.ai.ingest.AiIngestRunRequest;
import com.example.ai.ingest.AiIngestRunStatus;
import com.example.ai.ingest.AiIngestRunStatusResponse;
import com.example.ai.ingest.AiIngestRunSubmission;
import com.example.ai.ingest.RagIngestionPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiIngestOrchestrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-15T04:30:00Z");
    private static final UUID RUN_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID SECOND_RUN_ID = UUID.fromString("66666666-6666-4666-8666-666666666666");

    private RagIngestionPort port;
    private JobScheduler jobScheduler;
    private BaseballReadCacheInvalidator cacheInvalidator;
    private AiIngestProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private AiIngestOrchestrationService service;

    @BeforeEach
    void setUp() {
        port = org.mockito.Mockito.mock(RagIngestionPort.class);
        jobScheduler = org.mockito.Mockito.mock(JobScheduler.class);
        cacheInvalidator = org.mockito.Mockito.mock(BaseballReadCacheInvalidator.class);
        properties = new AiIngestProperties();
        properties.setTables(List.of("game", "game_metadata", "game_summary"));
        properties.setSeasonYear(2026);
        properties.setCheckInterval(Duration.ofSeconds(30));
        properties.setMonitoringDuration(Duration.ofHours(2));
        meterRegistry = new SimpleMeterRegistry();
        service = new AiIngestOrchestrationService(
                port,
                jobScheduler,
                cacheInvalidator,
                properties,
                new AiIngestMonitoringMetrics(meterRegistry),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void submitScheduledSubmitsExplicitRequestAndSchedulesFirstMonitor() {
        when(port.submit(any())).thenReturn(
                new AiIngestRunSubmission(RUN_ID, AiIngestRunStatus.QUEUED, false));

        service.submitScheduled();

        ArgumentCaptor<AiIngestRunRequest> requestCaptor = ArgumentCaptor.forClass(AiIngestRunRequest.class);
        verify(port).submit(requestCaptor.capture());
        assertThat(requestCaptor.getValue().tables())
                .containsExactly("game", "game_metadata", "game_summary");
        assertThat(requestCaptor.getValue().seasonYear()).isEqualTo(2026);
        assertThat(requestCaptor.getValue().mode()).isEqualTo("INCREMENTAL");
        assertThat(requestCaptor.getValue().triggerSource()).isEqualTo("BACKEND_SCHEDULED");
        verify(jobScheduler).schedule(any(UUID.class), eq(NOW.plusSeconds(30)), any(JobLambda.class));
        assertThat(meterRegistry.get("backend_ai_ingest_submissions_total")
                .tags("result", "accepted", "deduplicated", "false")
                .counter()
                .count()).isEqualTo(1.0d);
    }

    @Test
    void submitScheduledRecordsDeduplicatedAndFailedSubmissions() {
        when(port.submit(any())).thenReturn(
                new AiIngestRunSubmission(RUN_ID, AiIngestRunStatus.RUNNING, true));

        service.submitScheduled();

        assertThat(meterRegistry.get("backend_ai_ingest_submissions_total")
                .tags("result", "accepted", "deduplicated", "true")
                .counter()
                .count()).isEqualTo(1.0d);

        when(port.submit(any())).thenThrow(new IllegalStateException("upstream unavailable"));

        assertThatThrownBy(service::submitScheduled)
                .isInstanceOf(IllegalStateException.class);
        assertThat(meterRegistry.get("backend_ai_ingest_submissions_total")
                .tags("result", "failure", "deduplicated", "unknown")
                .counter()
                .count()).isEqualTo(1.0d);
    }

    @Test
    void queuedStatusSchedulesExactlyOneFutureMonitor() {
        when(port.getStatus(RUN_ID)).thenReturn(status(AiIngestRunStatus.QUEUED, Map.of()));

        service.monitor(RUN_ID, NOW.plus(Duration.ofHours(2)), 1);

        verify(jobScheduler).schedule(any(UUID.class), eq(NOW.plusSeconds(30)), any(JobLambda.class));
        verify(cacheInvalidator, never()).invalidateAll();
    }

    @Test
    void succeededStatusInvalidatesBaseballReadCaches() {
        when(port.getStatus(RUN_ID)).thenReturn(status(AiIngestRunStatus.SUCCEEDED, Map.of()));

        service.monitor(RUN_ID, NOW.plus(Duration.ofHours(2)), 1);

        verify(cacheInvalidator).invalidateAll();
        verify(jobScheduler, never()).schedule(any(UUID.class), any(Instant.class), any(JobLambda.class));
        assertThat(meterRegistry.get("backend_ai_ingest_monitor_terminal_total")
                .tag("status", "SUCCEEDED")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("backend_ai_ingest_orchestration_duration_seconds")
                .tag("status", "SUCCEEDED")
                .timer()
                .count()).isEqualTo(1L);
        assertThat(meterRegistry.get("backend_ai_ingest_cache_invalidations_total")
                .tag("result", "success")
                .counter()
                .count()).isEqualTo(1.0d);
    }

    @Test
    void succeededStatusRecordsCacheInvalidationFailure() {
        when(port.getStatus(RUN_ID)).thenReturn(status(AiIngestRunStatus.SUCCEEDED, Map.of()));
        doThrow(new IllegalStateException("cache unavailable"))
                .when(cacheInvalidator)
                .invalidateAll();

        assertThatThrownBy(() -> service.monitor(RUN_ID, NOW.plus(Duration.ofHours(2)), 1))
                .isInstanceOf(IllegalStateException.class);

        assertThat(meterRegistry.get("backend_ai_ingest_cache_invalidations_total")
                .tag("result", "failure")
                .counter()
                .count()).isEqualTo(1.0d);
    }

    @Test
    void terminalMetricsClampExtremePastAndFutureRequestedAtValues() {
        when(port.getStatus(RUN_ID)).thenReturn(
                status(AiIngestRunStatus.SUCCEEDED, Map.of(), Instant.MIN.toString()));
        when(port.getStatus(SECOND_RUN_ID)).thenReturn(
                status(AiIngestRunStatus.SUCCEEDED, Map.of(), Instant.MAX.toString()));

        service.monitor(RUN_ID, NOW.plus(Duration.ofHours(2)), 1);
        service.monitor(SECOND_RUN_ID, NOW.plus(Duration.ofHours(2)), 1);

        var timer = meterRegistry.get("backend_ai_ingest_orchestration_duration_seconds")
                .tag("status", "SUCCEEDED")
                .timer();
        assertThat(timer.count()).isEqualTo(2L);
        assertThat(timer.max(TimeUnit.HOURS)).isLessThanOrEqualTo(2.0d);
        verify(cacheInvalidator, org.mockito.Mockito.times(2)).invalidateAll();
    }

    @Test
    void failedStatusThrowsSanitizedFailure() {
        when(port.getStatus(RUN_ID)).thenReturn(status(
                AiIngestRunStatus.FAILED,
                Map.of("code", "INGEST_EXECUTION_FAILED", "message", "secret response body")));

        assertThatThrownBy(() -> service.monitor(RUN_ID, NOW.plus(Duration.ofHours(2)), 1))
                .isInstanceOf(AiIngestRunFailedException.class)
                .hasMessageContaining("INGEST_EXECUTION_FAILED")
                .hasMessageNotContaining("secret response body");
        assertThatThrownBy(() -> service.monitor(RUN_ID, NOW.plus(Duration.ofHours(2)), 1))
                .isInstanceOf(AiIngestRunFailedException.class);
        assertThat(meterRegistry.get("backend_ai_ingest_monitor_terminal_total")
                .tag("status", "FAILED")
                .counter()
                .count()).isEqualTo(1.0d);
    }

    @Test
    void manualStatusThrowsOnlyContractCodeAndOperatorSafeMessage() {
        when(port.getStatus(RUN_ID)).thenReturn(status(
                AiIngestRunStatus.MANUAL_BASEBALL_DATA_REQUIRED,
                Map.of(
                        "code", "MANUAL_BASEBALL_DATA_REQUIRED",
                        "operator_message", "Operator verified game_date is required.")));

        assertThatThrownBy(() -> service.monitor(RUN_ID, NOW.plus(Duration.ofHours(2)), 1))
                .isInstanceOf(AiIngestManualDataRequiredException.class)
                .hasMessageContaining("MANUAL_BASEBALL_DATA_REQUIRED")
                .hasMessageContaining("Operator verified game_date is required.");
        assertThatThrownBy(() -> service.monitor(RUN_ID, NOW.plus(Duration.ofHours(2)), 1))
                .isInstanceOf(AiIngestManualDataRequiredException.class);
        assertThat(meterRegistry.get("backend_ai_ingest_manual_data_required_total")
                .counter()
                .count()).isEqualTo(1.0d);
    }

    @Test
    void terminalStatusAtDeadlineIsHandledBeforeTimeout() {
        when(port.getStatus(RUN_ID)).thenReturn(status(AiIngestRunStatus.SUCCEEDED, Map.of()));

        service.monitor(RUN_ID, NOW, 1);

        verify(port).getStatus(RUN_ID);
        verify(cacheInvalidator).invalidateAll();
    }

    @Test
    void pendingStatusAtDeadlineTimesOutAfterFinalStatusCheck() {
        when(port.getStatus(RUN_ID)).thenReturn(status(AiIngestRunStatus.RUNNING, Map.of()));

        assertThatThrownBy(() -> service.monitor(RUN_ID, NOW, 1))
                .isInstanceOf(AiIngestRunFailedException.class)
                .hasMessageContaining("INGEST_MONITOR_TIMEOUT");

        verify(port).getStatus(RUN_ID);
        verify(jobScheduler, never()).schedule(any(UUID.class), any(Instant.class), any(JobLambda.class));
        assertThat(meterRegistry.get("backend_ai_ingest_monitor_terminal_total")
                .tag("status", "MONITOR_TIMEOUT")
                .counter()
                .count()).isEqualTo(1.0d);
    }

    @Test
    void retriedPendingMonitorUsesSequenceBasedNextJobIdWhenClockMoves() {
        when(port.getStatus(RUN_ID)).thenReturn(status(AiIngestRunStatus.QUEUED, Map.of()));
        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        Clock movingClock = org.mockito.Mockito.mock(Clock.class);
        when(movingClock.instant()).thenReturn(NOW, NOW.plusSeconds(5));
        AiIngestOrchestrationService movingClockService = new AiIngestOrchestrationService(
                port,
                jobScheduler,
                cacheInvalidator,
                properties,
                new AiIngestMonitoringMetrics(meterRegistry),
                movingClock);

        movingClockService.monitor(RUN_ID, NOW.plus(Duration.ofHours(2)), 7);
        movingClockService.monitor(RUN_ID, NOW.plus(Duration.ofHours(2)), 7);

        verify(jobScheduler, org.mockito.Mockito.times(2))
                .schedule(idCaptor.capture(), any(Instant.class), any(JobLambda.class));
        assertThat(idCaptor.getAllValues()).containsOnly(idCaptor.getAllValues().getFirst());
    }

    private AiIngestRunStatusResponse status(AiIngestRunStatus status, Map<String, Object> error) {
        return status(status, error, NOW.toString());
    }

    private AiIngestRunStatusResponse status(
            AiIngestRunStatus status,
            Map<String, Object> error,
            String requestedAt) {
        return new AiIngestRunStatusResponse(
                RUN_ID,
                status,
                "BACKEND_SCHEDULED",
                requestedAt,
                NOW.toString(),
                NOW.toString(),
                status == AiIngestRunStatus.RUNNING ? null : NOW.toString(),
                0,
                Map.of(),
                error);
    }
}
