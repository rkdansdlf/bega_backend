package com.example.prediction;

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
    private int totalPredictions;
    private int correctPredictions;
    private double accuracy;
    private int streak;
}
