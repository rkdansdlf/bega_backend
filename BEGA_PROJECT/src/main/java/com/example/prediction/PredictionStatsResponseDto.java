package com.example.prediction;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

public record PredictionStatsResponseDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean success,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String message,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        UserPredictionStatsDto data,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        Map<String, String> errors
) {
    public static PredictionStatsResponseDto success(String message, UserPredictionStatsDto data) {
        return new PredictionStatsResponseDto(true, message, data, null, null);
    }
}
