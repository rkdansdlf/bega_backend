package com.example.ai.ingest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record AiIngestRunSubmission(
        @JsonProperty("run_id") UUID runId,
        AiIngestRunStatus status,
        boolean deduplicated) {}
