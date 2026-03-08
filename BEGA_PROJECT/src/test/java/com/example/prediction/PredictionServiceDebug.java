package com.example.prediction;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Disabled("Manual debug test — requires live dev DB. Do not run in CI.")
@SpringBootTest
@ActiveProfiles("dev") // Use dev profile to connect to the DB where the issue is
public class PredictionServiceDebug {

    @Autowired
    private PredictionService predictionService;

    @Test
    public void debugGetGameDetail() {
        try {
            System.out.println("Starting debugGetGameDetail for 20251031LGHH0");
            GameDetailDto detail = predictionService.getGameDetail("20251031LGHH0");
            System.out.println("Success! Inning scores size: " + detail.getInningScores().size());
        } catch (Exception e) {
            System.err.println("EXCEPTION CAUGHT!");
            e.printStackTrace();
        }
    }
}
