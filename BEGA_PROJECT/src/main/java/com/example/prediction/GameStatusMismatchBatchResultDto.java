package com.example.prediction;

import java.time.LocalDate;
import java.util.List;

public record GameStatusMismatchBatchResultDto(
        LocalDate startDate,
        LocalDate endDate,
        int totalGames,
        int mismatchCount,
        List<GameStatusMismatchDto> mismatches,
        int nonCanonicalCount,
        List<NonCanonicalGameDto> nonCanonicalGames
) {
}
