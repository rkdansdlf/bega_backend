package com.example.auth.scheduler;

import com.example.auth.service.AccountDeletionService;
import com.example.auth.service.AccountSecurityService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.scheduling.cron.Cron;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountSecurityScheduler {

    private final JobScheduler jobScheduler;
    private final AccountSecurityService accountSecurityService;
    private final AccountDeletionService accountDeletionService;

    @PostConstruct
    void registerJobs() {
        jobScheduler.scheduleRecurrently("account-security-events-cleanup", Cron.daily(3, 30),
                this::cleanupSecurityEvents);
        jobScheduler.scheduleRecurrently("account-deletion-finalizer", Cron.daily(4, 0),
                this::finalizePendingDeletions);
    }

    @Job(name = "Cleanup Account Security Events")
    public void cleanupSecurityEvents() {
        accountSecurityService.cleanupOldSecurityEvents();
    }

    @Job(name = "Finalize Pending Account Deletions")
    public void finalizePendingDeletions() {
        accountDeletionService.finalizeDueDeletions();
    }
}
