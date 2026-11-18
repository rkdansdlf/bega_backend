package com.example.cheerboard.scheduler;

import com.example.cheerboard.service.CheerModerationCaseStaleMonitor;
import com.example.common.readonly.ReadOnlyVerificationPolicy;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.scheduling.cron.Cron;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!" + ReadOnlyVerificationPolicy.PROFILE)
@RequiredArgsConstructor
@Slf4j
public class CheerModerationCaseScheduler {

    private final JobScheduler jobScheduler;
    private final CheerModerationCaseStaleMonitor staleCaseMonitor;

    @PostConstruct
    void registerJobs() {
        jobScheduler.scheduleRecurrently("cheer-moderation-stale-case-check", Cron.daily(5, 30),
                this::checkStaleModerationCases);
    }

    @Job(name = "Check Stale Cheer Moderation Cases")
    public void checkStaleModerationCases() {
        int stale = staleCaseMonitor.checkStaleCases(java.time.LocalDateTime.now());
        log.info("Open cheer moderation cases older than 7 days: count={}", stale);
    }
}
