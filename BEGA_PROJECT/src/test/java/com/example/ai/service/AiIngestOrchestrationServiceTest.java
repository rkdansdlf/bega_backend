package com.example.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ai.config.AiIngestProperties;
import com.example.ai.ingest.AiIngestRunRequest;
import com.example.ai.ingest.AiIngestRunStatus;
import com.example.ai.ingest.AiIngestRunStatusResponse;
import com.example.ai.ingest.AiIngestRunSubmission;
import com.example.ai.ingest.RagIngestionPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiIngestOrchestrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-15T04:30:00Z");
    private static final UUID RUN_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");

    private RagIngestionPort port;
    private JobScheduler jobScheduler;
    private BaseballReadCacheInvalidator cacheInvalidator;
    private AiIngestProperties properties;
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
        service = new AiIngestOrchestrationService(
                port,
                jobScheduler,
                cacheInvalidator,
                properties,
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
                movingClock);

        movingClockService.monitor(RUN_ID, NOW.plus(Duration.ofHours(2)), 7);
        movingClockService.monitor(RUN_ID, NOW.plus(Duration.ofHours(2)), 7);

        verify(jobScheduler, org.mockito.Mockito.times(2))
                .schedule(idCaptor.capture(), any(Instant.class), any(JobLambda.class));
        assertThat(idCaptor.getAllValues()).containsOnly(idCaptor.getAllValues().getFirst());
    }

    private AiIngestRunStatusResponse status(AiIngestRunStatus status, Map<String, Object> error) {
        return new AiIngestRunStatusResponse(
                RUN_ID,
                status,
                "BACKEND_SCHEDULED",
                NOW.toString(),
                NOW.toString(),
                NOW.toString(),
                status == AiIngestRunStatus.RUNNING ? null : NOW.toString(),
                0,
                Map.of(),
                error);
    }
}
