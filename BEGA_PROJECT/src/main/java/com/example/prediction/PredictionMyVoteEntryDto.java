package com.example.prediction;

import io.swagger.v3.oas.annotations.media.Schema;

public record PredictionMyVoteEntryDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String gameId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, allowableValues = {"home", "away"})
        String votedTeam
) {
}
