package com.example.prediction;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPredictionStatsDto {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private int totalPredictions;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private int correctPredictions;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private double accuracy;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private int streak;
}
