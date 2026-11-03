package com.example.auth.scheduler;

import com.example.common.readonly.ReadOnlyVerificationPolicy;
import org.springframework.context.annotation.Profile;

import com.example.auth.service.AccountDeletionService;
import com.example.auth.service.AccountSecurityService;
import com.example.auth.service.AccountContentAnonymizationRetryService;
import com.example.auth.service.AccountDeletionStaleDeferralMonitor;
import com.example.auth.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.scheduling.cron.Cron;
import org.springframework.stereotype.Component;

@Component
@Profile("!" + ReadOnlyVerificationPolicy.PROFILE)
@RequiredArgsConstructor
@Slf4j
public class AccountSecurityScheduler {

    private final JobScheduler jobScheduler;
    private final AccountSecurityService accountSecurityService;
    private final AccountDeletionService accountDeletionService;
    private final AccountContentAnonymizationRetryService contentRetryService;
    private final UserRepository userRepository;
    private final AccountDeletionStaleDeferralMonitor staleDeferralMonitor;

    @PostConstruct
    void registerJobs() {
        jobScheduler.scheduleRecurrently("account-security-events-cleanup", Cron.daily(3, 30),
                this::cleanupSecurityEvents);
        jobScheduler.scheduleRecurrently("account-deletion-finalizer", Cron.daily(4, 0),
                this::finalizePendingDeletions);
        jobScheduler.scheduleRecurrently("account-deletion-stale-deferral-check", Cron.daily(5, 0),
                this::checkStaleDeferredDeletions);
        jobScheduler.scheduleRecurrently("account-content-anonymization-retry", "*/10 * * * *",
                this::retryContentAnonymization);
    }

    @Job(name = "Cleanup Account Security Events")
    public void cleanupSecurityEvents() {
        accountSecurityService.cleanupOldSecurityEvents();
    }

    @Job(name = "Check Stale Deferred Account Deletions")
    public void checkStaleDeferredDeletions() {
        // Run both checks even if one fails, then rethrow so JobRunr still records and retries the failure.
        RuntimeException failure = null;
        try {
            staleDeferralMonitor.checkStaleDeferrals(java.time.LocalDateTime.now());
        } catch (RuntimeException ex) {
            failure = ex;
        }
        try {
            staleDeferralMonitor.checkStaleContentRetries(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        } catch (RuntimeException ex) {
            if (failure == null) {
                failure = ex;
            } else {
                failure.addSuppressed(ex);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Job(name = "Finalize Pending Account Deletions")
    public void finalizePendingDeletions() {
        accountDeletionService.finalizeDueDeletions();
    }

    @Job(name = "Retry Account Content Anonymization")
    public void retryContentAnonymization() {
        var ids = userRepository.findDueContentAnonymizationIds(
                java.time.LocalDateTime.now(java.time.ZoneOffset.UTC),
                org.springframework.data.domain.PageRequest.of(0, 100));
        for (Long id : ids) {
            try {
                contentRetryService.retry(id);
            } catch (RuntimeException ex) {
                log.warn("Account content anonymization retry failed: userId={}", id, ex);
                try {
                    contentRetryService.defer(id);
                } catch (RuntimeException deferError) {
                    log.warn("Account content anonymization deferral failed: userId={}", id, deferError);
                }
            }
        }
    }
}
