package com.example.prediction;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserPredictionStatsDto {
    private int totalPredictions;
    private int correctPredictions;
    private double accuracy;
    private int streak;
}
