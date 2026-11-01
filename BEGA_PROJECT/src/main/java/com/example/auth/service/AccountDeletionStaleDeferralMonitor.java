package com.example.auth.service;

import com.example.auth.repository.UserRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Surfaces account deletions that are overdue, i.e. still pending well after their due time. A post purge,
 * an open moderation case, or a finalizer failure can keep them from completing. Reports the count as a gauge
 * and logs a sample of user ids (never identity data) so operators can act on them.
 *
 * <p>Also watches tombstoned accounts whose content anonymization retry has stopped running. Each attempt moves
 * the retry marker forward, so a marker older than the threshold means the retry loop has not reached the account
 * for that long. Accounts that are repeatedly held by a moderation report are not counted here; the
 * {@link ModerationHoldAlertService} already logs those per account.
 */
@Slf4j
@Service
public class AccountDeletionStaleDeferralMonitor {

    static final int STALE_AFTER_DAYS = 30;
    /** The retry loop runs every 10 minutes, so a day without progress means it has stopped, not slowed down. */
    static final Duration STALE_CONTENT_RETRY_AFTER = Duration.ofHours(24);
    private static final int SAMPLE_SIZE = 20;

    private final UserRepository userRepository;
    private final AtomicLong staleCount = new AtomicLong();
    private final AtomicLong staleContentRetryCount = new AtomicLong();

    public AccountDeletionStaleDeferralMonitor(UserRepository userRepository, MeterRegistry meterRegistry) {
        this.userRepository = userRepository;
        Gauge.builder("account.deletion.stale_deferred", staleCount, AtomicLong::get)
                .description("Pending account deletions overdue by more than " + STALE_AFTER_DAYS + " days")
                .register(meterRegistry);
        Gauge.builder("account.deletion.stale_content_retry", staleContentRetryCount, AtomicLong::get)
                .description("Deleted accounts whose content anonymization retry has not run for more than "
                        + STALE_CONTENT_RETRY_AFTER.toHours() + " hours")
                .register(meterRegistry);
    }

    /** {@code now} is the JVM-local clock, which is what {@code deletionScheduledFor} is written with. */
    @Transactional(readOnly = true)
    public long checkStaleDeferrals(LocalDateTime now) {
        LocalDateTime cutoff = now.minusDays(STALE_AFTER_DAYS);
        long count = userRepository.countStaleDeferredDeletions(cutoff);
        staleCount.set(count);
        if (count > 0) {
            List<Long> sampleUserIds = userRepository.findStaleDeferredDeletionUserIds(
                    cutoff, PageRequest.of(0, SAMPLE_SIZE));
            log.warn("Account deletions overdue by more than {} days: count={}, sampleUserIds={}",
                    STALE_AFTER_DAYS, count, sampleUserIds);
        }
        return count;
    }

    /** {@code nowUtc} must be UTC, which is what {@code contentAnonymizationRetryAt} is written with. */
    @Transactional(readOnly = true)
    public long checkStaleContentRetries(LocalDateTime nowUtc) {
        LocalDateTime cutoff = nowUtc.minus(STALE_CONTENT_RETRY_AFTER);
        long count = userRepository.countStaleContentRetries(cutoff);
        staleContentRetryCount.set(count);
        if (count > 0) {
            List<Long> sampleUserIds = userRepository.findStaleContentRetryUserIds(
                    cutoff, PageRequest.of(0, SAMPLE_SIZE));
            log.warn("Account content anonymization retries stalled for more than {} hours: count={}, sampleUserIds={}",
                    STALE_CONTENT_RETRY_AFTER.toHours(), count, sampleUserIds);
        }
        return count;
    }
}
