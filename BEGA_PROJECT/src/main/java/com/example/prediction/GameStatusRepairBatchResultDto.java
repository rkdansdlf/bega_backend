package com.example.prediction;

import java.time.LocalDate;
import java.util.List;

public record GameStatusRepairBatchResultDto(
        LocalDate startDate,
        LocalDate endDate,
        boolean dryRun,
        int totalGames,
        int mismatchCount,
        int repairedCount,
        List<GameStatusMismatchDto> mismatches,
        List<GameScoreSyncResultDto> repairedGames
) {
}
