package com.example.cheerboard.scheduler;

import com.example.cheerboard.service.AiIntegrationService;
import lombok.RequiredArgsConstructor;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.scheduling.cron.Cron;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiIngestScheduler implements ApplicationRunner {

    private final JobScheduler jobScheduler;
    private final AiIntegrationService aiIntegrationService;

    @Override
    public void run(ApplicationArguments args) {
        // 매일 새벽 04:30에 AI RAG 인덱싱 작업 실행
        // (04:00 크롤링/통계 업데이트가 완료된 후 실행)
        jobScheduler.scheduleRecurrently("ai-rag-ingestion", Cron.daily(4, 30),
                aiIntegrationService::triggerRagIngestion);
    }
}
