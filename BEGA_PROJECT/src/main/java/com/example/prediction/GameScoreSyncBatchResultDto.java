package com.example.prediction;

import java.time.LocalDate;
import java.util.List;

public record GameScoreSyncBatchResultDto(
        LocalDate startDate,
        LocalDate endDate,
        int totalGames,
        int syncedGames,
        int skippedGames,
        List<GameScoreSyncResultDto> results
) {
}
