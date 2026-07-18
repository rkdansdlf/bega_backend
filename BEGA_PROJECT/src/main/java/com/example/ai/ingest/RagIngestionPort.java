package com.example.ai.ingest;

import java.util.UUID;

public interface RagIngestionPort {

    AiIngestRunSubmission submit(AiIngestRunRequest request);

    AiIngestRunStatusResponse getStatus(UUID runId);
}
