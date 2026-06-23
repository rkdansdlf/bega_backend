package com.example.prediction;

import io.swagger.v3.oas.annotations.media.Schema;

public record PredictionBootstrapResourceDto<T>(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean ok,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        T data,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        PredictionBootstrapErrorDto error
) {
    public static <T> PredictionBootstrapResourceDto<T> success(T data) {
        return new PredictionBootstrapResourceDto<>(true, data, null);
    }

    public static <T> PredictionBootstrapResourceDto<T> failure(PredictionBootstrapErrorDto error) {
        return new PredictionBootstrapResourceDto<>(false, null, error);
    }
}
