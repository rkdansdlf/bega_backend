package com.example.prediction;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RankingPredictionContextSnapshot {

    private Map<String, Integer> currentRankMap = new HashMap<>();
    private Map<String, Integer> lastRankMap = new HashMap<>();
    private Map<String, String> teamNameMap = new HashMap<>();

    public RankingPredictionContextSnapshot(
            Map<String, Integer> currentRankMap,
            Map<String, Integer> lastRankMap,
            Map<String, String> teamNameMap
    ) {
        this.currentRankMap = currentRankMap == null ? new HashMap<>() : new HashMap<>(currentRankMap);
        this.lastRankMap = lastRankMap == null ? new HashMap<>() : new HashMap<>(lastRankMap);
        this.teamNameMap = teamNameMap == null ? new HashMap<>() : new HashMap<>(teamNameMap);
    }
}
