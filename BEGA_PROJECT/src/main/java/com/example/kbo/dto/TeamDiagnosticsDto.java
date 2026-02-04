package com.example.kbo.dto;

import java.util.List;

public record TeamDiagnosticsDto(
        List<String> canonicalCodes,
        List<CodeCount> nonCanonicalHomeTeams,
        List<CodeCount> nonCanonicalAwayTeams,
        List<CodeCount> nonCanonicalWinningTeams
) {
    public record CodeCount(String code, long count) {
    }
}
