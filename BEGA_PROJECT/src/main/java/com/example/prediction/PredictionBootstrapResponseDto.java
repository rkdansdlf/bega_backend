package com.example.prediction;

import io.swagger.v3.oas.annotations.media.Schema;

public record PredictionBootstrapResponseDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        MatchDayNavigationResponseDto schedule,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        String selectedGameId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean selectedGameFound,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        PredictionBootstrapResourceDto<GameDetailDto> detail,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        PredictionBootstrapResourceDto<PredictionResponseDto> voteStatus
) {
}
