package com.example.mate.service;

import com.example.mate.entity.Party;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class MatePartyListMetricsService {

    static final String REQUEST_DURATION_METRIC = "mate_party_list_request_duration_seconds";

    private static final Set<String> ALLOWED_SORTS = Set.of("createdAt", "gameDate", "currentParticipants");

    private final MeterRegistry meterRegistry;

    public MatePartyListMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordRequest(
            String teamId,
            String stadium,
            LocalDate gameDate,
            String searchQuery,
            String status,
            String sortBy,
            String sortDir,
            int pageSize,
            boolean authenticated,
            String result,
            long durationNanos) {
        if (durationNanos < 0) {
            return;
        }

        Timer.builder(REQUEST_DURATION_METRIC)
                .description("Mate party list request duration")
                .publishPercentileHistogram()
                .tags(
                        "team_filter", presence(teamId),
                        "stadium_filter", presence(stadium),
                        "date_filter", gameDate == null ? "absent" : "present",
                        "search_filter", presence(searchQuery),
                        "status", normalizeStatus(status),
                        "sort", normalizeSort(sortBy),
                        "sort_dir", normalizeSortDir(sortDir),
                        "size_bucket", pageSizeBucket(pageSize),
                        "authenticated", authenticated ? "true" : "false",
                        "result", normalizeResult(result))
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private String presence(String value) {
        return value == null || value.isBlank() ? "absent" : "present";
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "all";
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        try {
            return Party.PartyStatus.valueOf(normalized).name().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ex) {
            return "invalid";
        }
    }

    private String normalizeSort(String sortBy) {
        if (sortBy == null || !ALLOWED_SORTS.contains(sortBy)) {
            return "createdat";
        }
        return sortBy.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeSortDir(String sortDir) {
        return sortDir != null && sortDir.equalsIgnoreCase("asc") ? "asc" : "desc";
    }

    private String pageSizeBucket(int pageSize) {
        if (pageSize <= 0) {
            return "invalid";
        }
        if (pageSize <= 9) {
            return "1_9";
        }
        if (pageSize <= 20) {
            return "10_20";
        }
        if (pageSize <= 30) {
            return "21_30";
        }
        return "over_30";
    }

    private String normalizeResult(String result) {
        if ("success".equals(result) || "failure".equals(result)) {
            return result;
        }
        return "unknown";
    }
}
