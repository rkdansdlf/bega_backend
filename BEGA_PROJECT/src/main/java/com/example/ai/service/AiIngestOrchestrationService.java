package com.example.ai.service;

import com.example.ai.config.AiIngestProperties;
import com.example.ai.ingest.AiIngestRunRequest;
import com.example.ai.ingest.AiIngestRunStatusResponse;
import com.example.ai.ingest.AiIngestRunSubmission;
import com.example.ai.ingest.RagIngestionPort;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
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
    private final Clock clock;

    @Autowired
    public AiIngestOrchestrationService(
            RagIngestionPort ragIngestionPort,
            JobScheduler jobScheduler,
            BaseballReadCacheInvalidator cacheInvalidator,
            AiIngestProperties properties) {
        this(ragIngestionPort, jobScheduler, cacheInvalidator, properties, Clock.systemUTC());
    }

    AiIngestOrchestrationService(
            RagIngestionPort ragIngestionPort,
            JobScheduler jobScheduler,
            BaseballReadCacheInvalidator cacheInvalidator,
            AiIngestProperties properties,
            Clock clock) {
        this.ragIngestionPort = ragIngestionPort;
        this.jobScheduler = jobScheduler;
        this.cacheInvalidator = cacheInvalidator;
        this.properties = properties;
        this.clock = clock;
    }

    @Job(name = "Submit scheduled AI ingestion run")
    public void submitScheduled() {
        AiIngestRunRequest request = new AiIngestRunRequest(
                properties.getTables(),
                properties.getSeasonYear(),
                "INCREMENTAL",
                "BACKEND_SCHEDULED");
        AiIngestRunSubmission submission = ragIngestionPort.submit(request);
        UUID runId = Objects.requireNonNull(submission.runId(), "AI ingestion submission is missing run_id");
        Instant now = clock.instant();
        Instant deadline = now.plus(properties.getMonitoringDuration());
        scheduleMonitor(runId, deadline, now.plus(properties.getCheckInterval()));
        log.info("AI ingestion monitoring scheduled runId={} status={} deduplicated={}",
                runId, submission.status(), submission.deduplicated());
    }

    @Job(name = "Monitor AI ingestion run %0")
    public void monitor(UUID runId, Instant deadline) {
        Instant now = clock.instant();
        AiIngestRunStatusResponse response = ragIngestionPort.getStatus(runId);
        switch (response.status()) {
            case QUEUED, RUNNING -> {
                if (!now.isBefore(deadline)) {
                    throw new AiIngestRunFailedException("INGEST_MONITOR_TIMEOUT");
                }
                Instant nextCheck = now.plus(properties.getCheckInterval());
                scheduleMonitor(runId, deadline, nextCheck.isAfter(deadline) ? deadline : nextCheck);
            }
            case SUCCEEDED -> cacheInvalidator.invalidateAll();
            case FAILED -> throw new AiIngestRunFailedException(errorValue(response.error(), "code"));
            case MANUAL_BASEBALL_DATA_REQUIRED -> throw new AiIngestManualDataRequiredException(
                    firstNonBlank(
                            errorValue(response.error(), "operator_message"),
                            errorValue(response.error(), "message")));
        }
    }

    private void scheduleMonitor(UUID runId, Instant deadline, Instant scheduledAt) {
        UUID jobId = UUID.nameUUIDFromBytes(
                ("ai-ingest-monitor:" + runId + ":" + scheduledAt)
                        .getBytes(StandardCharsets.UTF_8));
        jobScheduler.schedule(jobId, scheduledAt, () -> monitor(runId, deadline));
    }

    private String errorValue(Map<String, Object> error, String key) {
        Object value = error == null ? null : error.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
