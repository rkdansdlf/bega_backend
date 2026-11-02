package com.example.cheerboard.service;

import com.example.cheerboard.repo.CheerReportRepo;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Raises the operator alert for open moderation cases that stay undecided. A case is never released automatically,
 * because the evidence it protects may still be needed. Each case alerts once per tier: a warning at 7 days and at
 * 14 days, and an error at 30 days, which is the escalation. Logs carry the report id and the age, never the content.
 */
@Slf4j
@Service
public class CheerModerationCaseStaleMonitor {

    /** Tier thresholds in ascending order. Tier n is reached once a case is at least {@code TIERS.get(n - 1)} old. */
    static final List<Duration> TIERS = List.of(Duration.ofDays(7), Duration.ofDays(14), Duration.ofDays(30));
    private static final int ESCALATION_TIER = TIERS.size();

    private final CheerReportRepo reportRepo;
    private final AtomicLong staleCount = new AtomicLong();
    /** Highest tier already alerted per open report, so every tier logs once. Closed cases are dropped on the next run. */
    private final Map<Long, Integer> alertedTier = new ConcurrentHashMap<>();

    public CheerModerationCaseStaleMonitor(CheerReportRepo reportRepo, MeterRegistry meterRegistry) {
        this.reportRepo = reportRepo;
        Gauge.builder("cheer.moderation.stale_open_cases", staleCount, AtomicLong::get)
                .description("Open moderation cases older than " + TIERS.get(0).toDays() + " days")
                .register(meterRegistry);
    }

    /** {@code now} is the JVM-local clock, which is what {@code createdat} is written with. Returns the stale count. */
    @Transactional(readOnly = true)
    public int checkStaleCases(LocalDateTime now) {
        List<Object[]> rows = reportRepo.findOpenCasesCreatedBefore(now.minus(TIERS.get(0)));
        staleCount.set(rows.size());
        Set<Long> stillOpen = new HashSet<>();
        for (Object[] row : rows) {
            long reportId = ((Number) row[0]).longValue();
            Duration age = Duration.between(toLocalDateTime(row[1]), now);
            stillOpen.add(reportId);
            int tier = tierFor(age);
            if (tier > alertedTier.getOrDefault(reportId, 0)) {
                alertedTier.put(reportId, tier);
                alert(tier, reportId, age);
            }
        }
        alertedTier.keySet().retainAll(stillOpen);
        return rows.size();
    }

    /** Number of thresholds the age has reached. Callers only pass ages at or above the first threshold. */
    static int tierFor(Duration age) {
        int tier = 0;
        for (Duration threshold : TIERS) {
            if (age.compareTo(threshold) >= 0) {
                tier++;
            }
        }
        return tier;
    }

    private void alert(int tier, long reportId, Duration age) {
        if (tier == ESCALATION_TIER) {
            log.error("Moderation case open for {} days, escalating to an administrator: reportId={}",
                    age.toDays(), reportId);
        } else {
            log.warn("Moderation case still open after {} days: reportId={}", age.toDays(), reportId);
        }
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return (LocalDateTime) value;
    }
}
