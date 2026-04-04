package com.example.kbo.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

public final class GameStatusResolver {

    private static final Set<String> SCHEDULED_STATUSES = Set.of(
            "",
            "UNKNOWN",
            "TBD",
            "PENDING",
            "READY",
            "NOT_STARTED",
            "NONE",
            "SCHEDULED"
    );
    private static final Set<String> COMPLETED_STATUSES = Set.of(
            "COMPLETED",
            "FINAL",
            "FINISHED",
            "DONE",
            "END",
            "E",
            "F"
    );
    private static final Set<String> LIVE_STATUSES = Set.of(
            "LIVE",
            "IN_PROGRESS",
            "INPROGRESS",
            "PLAYING"
    );

    private GameStatusResolver() {
    }

    public static String resolveEffectiveStatus(
            String rawStatus,
            LocalDate gameDate,
            LocalTime startTime,
            Integer homeScore,
            Integer awayScore,
            boolean hasProgressData
    ) {
        String normalizedStatus = rawStatus == null ? "" : rawStatus.trim().toUpperCase();
        boolean hasKnownScore = hasKnownScore(homeScore, awayScore);

        if ("POSTPONED".equals(normalizedStatus) || "CANCELLED".equals(normalizedStatus)) {
            return normalizedStatus;
        }
        if ("DRAW".equals(normalizedStatus)) {
            return "DRAW";
        }
        if (COMPLETED_STATUSES.contains(normalizedStatus)) {
            return hasKnownScore && homeScore.equals(awayScore) ? "DRAW" : "COMPLETED";
        }
        if (LIVE_STATUSES.contains(normalizedStatus)) {
            return "LIVE";
        }
        if (SCHEDULED_STATUSES.contains(normalizedStatus) && hasProgressData) {
            return resolveSnapshotStatus(gameDate, startTime, homeScore, awayScore);
        }

        return normalizedStatus.isEmpty() ? rawStatus : normalizedStatus;
    }

    public static String resolveSnapshotStatus(
            LocalDate gameDate,
            LocalTime startTime,
            Integer homeScore,
            Integer awayScore
    ) {
        LocalDate today = LocalDate.now();
        boolean hasKnownScore = hasKnownScore(homeScore, awayScore);

        if (gameDate != null && gameDate.isBefore(today)) {
            return hasKnownScore && homeScore.equals(awayScore) ? "DRAW" : "COMPLETED";
        }

        if (gameDate != null && startTime != null) {
            LocalDateTime startDateTime = LocalDateTime.of(gameDate, startTime);
            if (!LocalDateTime.now().isBefore(startDateTime)) {
                return "LIVE";
            }
        }

        if (gameDate != null && gameDate.isEqual(today)) {
            return "LIVE";
        }

        return "SCHEDULED";
    }

    private static boolean hasKnownScore(Integer homeScore, Integer awayScore) {
        return homeScore != null && awayScore != null;
    }
}
