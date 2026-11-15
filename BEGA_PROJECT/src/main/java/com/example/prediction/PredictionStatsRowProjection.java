package com.example.prediction;

import java.time.LocalDateTime;

public interface PredictionStatsRowProjection {

    Long getId();

    String getGameId();

    String getVotedTeam();

    LocalDateTime getCreatedAt();
}
