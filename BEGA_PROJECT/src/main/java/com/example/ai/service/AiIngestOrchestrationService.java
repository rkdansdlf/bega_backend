package com.example.ai.service;

import com.example.ai.config.AiIngestProperties;
import com.example.ai.ingest.AiIngestRunRequest;
import com.example.ai.ingest.AiIngestRunStatusResponse;
import com.example.ai.ingest.AiIngestRunSubmission;
import com.example.ai.ingest.RagIngestionPort;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AiIngestOrchestrationService {

    private final RagIngestionPort ragIngestionPort;
    private final JobScheduler jobScheduler;
    private final BaseballReadCacheInvalidator cacheInvalidator;
    private final AiIngestProperties properties;
    private final AiIngestMonitoringMetrics metrics;
    private final Clock clock;

    @Autowired
    public AiIngestOrchestrationService(
            RagIngestionPort ragIngestionPort,
            JobScheduler jobScheduler,
            BaseballReadCacheInvalidator cacheInvalidator,
            AiIngestProperties properties,
            AiIngestMonitoringMetrics metrics) {
        this(ragIngestionPort, jobScheduler, cacheInvalidator, properties, metrics, Clock.systemUTC());
    }

    AiIngestOrchestrationService(
            RagIngestionPort ragIngestionPort,
            JobScheduler jobScheduler,
            BaseballReadCacheInvalidator cacheInvalidator,
            AiIngestProperties properties,
            AiIngestMonitoringMetrics metrics,
            Clock clock) {
        this.ragIngestionPort = ragIngestionPort;
        this.jobScheduler = jobScheduler;
        this.cacheInvalidator = cacheInvalidator;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Job(name = "Submit scheduled AI ingestion run")
    public void submitScheduled() {
        AiIngestRunRequest request = new AiIngestRunRequest(
                properties.getTables(),
                properties.getSeasonYear(),
                "INCREMENTAL",
                "BACKEND_SCHEDULED");
        AiIngestRunSubmission submission;
        UUID runId;
        try {
            submission = ragIngestionPort.submit(request);
            runId = Objects.requireNonNull(submission.runId(), "AI ingestion submission is missing run_id");
            metrics.recordSubmissionAccepted(submission.deduplicated());
        } catch (RuntimeException exception) {
            metrics.recordSubmissionFailure();
            throw exception;
        }
        Instant now = clock.instant();
        Instant deadline = now.plus(properties.getMonitoringDuration());
        scheduleMonitor(runId, deadline, now.plus(properties.getCheckInterval()), 1);
        log.info("AI ingestion monitoring scheduled runId={} status={} deduplicated={}",
                runId, submission.status(), submission.deduplicated());
    }

    @Job(name = "Monitor AI ingestion run %0")
    public void monitor(UUID runId, Instant deadline, long pollSequence) {
        Instant now = clock.instant();
        AiIngestRunStatusResponse response = ragIngestionPort.getStatus(runId);
        switch (response.status()) {
            case QUEUED, RUNNING -> {
                if (!now.isBefore(deadline)) {
                    metrics.recordTerminal(
                            runId,
                            "MONITOR_TIMEOUT",
                            orchestrationElapsed(response, now, deadline));
                    throw new AiIngestRunFailedException("INGEST_MONITOR_TIMEOUT");
                }
                Instant nextCheck = now.plus(properties.getCheckInterval());
                scheduleMonitor(
                        runId,
                        deadline,
                        nextCheck.isAfter(deadline) ? deadline : nextCheck,
                        pollSequence + 1);
            }
            case SUCCEEDED -> {
                metrics.recordTerminal(
                        runId,
                        response.status().name(),
                        orchestrationElapsed(response, now, deadline));
                try {
                    cacheInvalidator.invalidateAll();
                    metrics.recordCacheInvalidation(true);
                } catch (RuntimeException exception) {
                    metrics.recordCacheInvalidation(false);
                    throw exception;
                }
            }
            case FAILED -> {
                metrics.recordTerminal(
                        runId,
                        response.status().name(),
                        orchestrationElapsed(response, now, deadline));
                throw new AiIngestRunFailedException(errorValue(response.error(), "code"));
            }
            case MANUAL_BASEBALL_DATA_REQUIRED -> {
                metrics.recordTerminal(
                        runId,
                        response.status().name(),
                        orchestrationElapsed(response, now, deadline));
                metrics.recordManualDataRequired(runId);
                throw new AiIngestManualDataRequiredException(
                        firstNonBlank(
                                errorValue(response.error(), "operator_message"),
                                errorValue(response.error(), "message")));
            }
        }
    }

    private Duration orchestrationElapsed(
            AiIngestRunStatusResponse response,
            Instant now,
            Instant deadline) {
        Instant submissionWindowStart = deadline.minus(properties.getMonitoringDuration());
        Instant startedAt = submissionWindowStart;
        try {
            if (response.requestedAt() != null && !response.requestedAt().isBlank()) {
                Instant requestedAt = Instant.parse(response.requestedAt());
                if (requestedAt.isBefore(submissionWindowStart)) {
                    startedAt = submissionWindowStart;
                } else if (requestedAt.isAfter(now)) {
                    startedAt = now;
                } else {
                    startedAt = requestedAt;
                }
            }
        } catch (DateTimeException ignored) {
            // Fall back to the backend submission window without exposing the raw value.
        }
        Duration elapsed = Duration.between(startedAt, now);
        return elapsed.isNegative() ? Duration.ZERO : elapsed;
    }

    private void scheduleMonitor(
            UUID runId,
            Instant deadline,
            Instant scheduledAt,
            long pollSequence) {
        UUID jobId = UUID.nameUUIDFromBytes(
                ("ai-ingest-monitor:" + runId + ":" + pollSequence)
                        .getBytes(StandardCharsets.UTF_8));
        jobScheduler.schedule(jobId, scheduledAt, () -> monitor(runId, deadline, pollSequence));
    }

    private String errorValue(Map<String, Object> error, String key) {
        Object value = error == null ? null : error.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
