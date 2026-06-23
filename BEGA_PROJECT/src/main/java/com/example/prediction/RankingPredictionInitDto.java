package com.example.prediction;

import io.swagger.v3.oas.annotations.media.Schema;

public record RankingPredictionInitDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int seasonYear,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        RankingPredictionResponseDto saved
) {}
