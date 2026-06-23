package com.example.prediction;

import io.swagger.v3.oas.annotations.media.Schema;

public record PredictionBootstrapErrorDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String message,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        Integer status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        String code
) {
}
