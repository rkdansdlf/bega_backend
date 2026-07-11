package com.example.cheerboard.service;

import com.example.cheerboard.service.port.RagIngestionPort;
import lombok.RequiredArgsConstructor;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiIntegrationService {

    private final RagIngestionPort ragIngestionPort;

    @Job(name = "Trigger RAG Pipeline Ingestion")
    public void triggerRagIngestion() {
        ragIngestionPort.trigger();
    }
}
