package com.example.cheerboard.retention;

import com.example.common.readonly.ReadOnlyVerificationPolicy;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.scheduling.cron.Cron;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!" + ReadOnlyVerificationPolicy.PROFILE)
@RequiredArgsConstructor
public class ReportEvidenceMediaRetentionScheduler {

    private final JobScheduler jobScheduler;
    private final ReportEvidenceMediaRetentionService retentionService;

    @PostConstruct
    void registerJobs() {
        jobScheduler.scheduleRecurrently("report-evidence-media-retention", Cron.daily(4, 45),
                this::runRetention);
    }

    @Job(name = "Report Evidence Media Retention")
    public void runRetention() {
        retentionService.runDaily(LocalDateTime.now());
    }
}
