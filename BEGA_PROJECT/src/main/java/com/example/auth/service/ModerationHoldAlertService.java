package com.example.auth.service;

import com.example.cheerboard.repo.CheerReportRepo;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logs an error when a moderation report has held a deleted account's content for longer than {@link #ALERT_AFTER}.
 * Runs in its own transaction so that a failing check cannot roll back the content retry that calls it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationHoldAlertService {
    /** A report filed longer ago than this keeps the account's content frozen and needs operator attention. */
    static final Duration ALERT_AFTER = Duration.ofHours(24);

    private final CheerReportRepo cheerReportRepo;
    /** Day of the last alert per account; keeps the every-10-minute retry from flooding the logs. */
    private final Map<Long, LocalDate> alertedOn = new ConcurrentHashMap<>();

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void alertIfStale(Long userId) {
        LocalDate today = LocalDate.now();
        if (today.equals(alertedOn.get(userId))) return;
        // createdat is written with LocalDateTime.now(), so the cutoff uses the same clock.
        if (!cheerReportRepo.existsOpenModerationOlderThan(userId, LocalDateTime.now().minus(ALERT_AFTER))) return;
        alertedOn.put(userId, today);
        log.error("Account content anonymization is held by a moderation report filed more than {} ago: userId={}",
                ALERT_AFTER, userId);
    }

    /** Drops the record once the account's content is finalized, so the map only tracks accounts still held. */
    public void forget(Long userId) {
        alertedOn.remove(userId);
    }
}
