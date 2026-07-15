package com.example.ai.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.ai.config.AiIngestProperties;
import com.example.ai.service.AiIngestOrchestrationService;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

class AiIngestSchedulerTest {

    @Test
    void runRegistersOnlyConfiguredRecurringSubmission() {
        JobScheduler jobScheduler = org.mockito.Mockito.mock(JobScheduler.class);
        AiIngestOrchestrationService service = org.mockito.Mockito.mock(AiIngestOrchestrationService.class);
        AiIngestProperties properties = new AiIngestProperties();
        properties.setEnabled(true);
        properties.setCron("30 4 * * *");
        AiIngestScheduler scheduler = new AiIngestScheduler(jobScheduler, service, properties);

        scheduler.run(org.mockito.Mockito.mock(ApplicationArguments.class));

        verify(jobScheduler).scheduleRecurrently(
                eq("ai-rag-ingestion"),
                eq("30 4 * * *"),
                any(JobLambda.class));
    }

    @Test
    void runDoesNotRegisterRecurringSubmissionWhenDisabled() {
        JobScheduler jobScheduler = org.mockito.Mockito.mock(JobScheduler.class);
        AiIngestOrchestrationService service = org.mockito.Mockito.mock(AiIngestOrchestrationService.class);
        AiIngestProperties properties = new AiIngestProperties();
        properties.setEnabled(false);
        AiIngestScheduler scheduler = new AiIngestScheduler(jobScheduler, service, properties);

        scheduler.run(null);

        verify(jobScheduler, never()).scheduleRecurrently(
                any(String.class), any(String.class), any(JobLambda.class));
        verify(jobScheduler).deleteRecurringJob("ai-rag-ingestion");
    }
}
