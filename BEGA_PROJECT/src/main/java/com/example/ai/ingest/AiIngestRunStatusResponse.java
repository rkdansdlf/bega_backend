package com.example.ai.ingest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.UUID;

public record AiIngestRunStatusResponse(
        @JsonProperty("run_id") UUID runId,
        AiIngestRunStatus status,
        @JsonProperty("trigger_source") String triggerSource,
        @JsonProperty("requested_at") String requestedAt,
        @JsonProperty("started_at") String startedAt,
        @JsonProperty("heartbeat_at") String heartbeatAt,
        @JsonProperty("finished_at") String finishedAt,
        @JsonProperty("recovery_attempts") int recoveryAttempts,
        Map<String, Object> tables,
        Map<String, Object> error) {

    public AiIngestRunStatusResponse {
        tables = tables == null ? Map.of() : Map.copyOf(tables);
        error = error == null ? Map.of() : Map.copyOf(error);
    }
}
