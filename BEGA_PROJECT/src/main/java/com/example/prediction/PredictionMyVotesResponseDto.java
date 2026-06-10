package com.example.prediction;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

public record PredictionMyVotesResponseDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Map<String, String> votes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<PredictionMyVoteEntryDto> entries
) {
    public PredictionMyVotesResponseDto(Map<String, String> votes) {
        this(votes, toEntries(votes));
    }

    private static List<PredictionMyVoteEntryDto> toEntries(Map<String, String> votes) {
        if (votes == null || votes.isEmpty()) {
            return List.of();
        }

        return votes.entrySet().stream()
                .map(entry -> new PredictionMyVoteEntryDto(entry.getKey(), entry.getValue()))
                .toList();
    }
}
