package com.example.prediction;

import io.swagger.v3.oas.annotations.media.Schema;

public record RankingPredictionCurrentSeasonDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int seasonYear
) {
}
