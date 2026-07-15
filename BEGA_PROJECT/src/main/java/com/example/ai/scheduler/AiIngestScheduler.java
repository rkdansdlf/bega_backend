package com.example.ai.scheduler;

import com.example.ai.config.AiIngestProperties;
import com.example.ai.service.AiIngestOrchestrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiIngestScheduler implements ApplicationRunner {

    private final JobScheduler jobScheduler;
    private final AiIngestOrchestrationService orchestrationService;
    private final AiIngestProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.info("AI ingestion scheduler is disabled");
            return;
        }

        jobScheduler.scheduleRecurrently(
                "ai-rag-ingestion",
                properties.getCron(),
                orchestrationService::submitScheduled);
        log.info("AI ingestion scheduler registered cron={}", properties.getCron());
    }
}
