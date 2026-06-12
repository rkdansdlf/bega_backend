package com.example.prediction;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PredictionWarmupService {

    private final PredictionService predictionService;
    private final Clock clock;
    private final boolean enabled;
    private final boolean detailWarmupEnabled;
    private final boolean voteStatusWarmupEnabled;

    @Autowired
    public PredictionWarmupService(
            PredictionService predictionService,
            @Value("${app.prediction.warmup.enabled:true}") boolean enabled,
            @Value("${app.prediction.warmup.detail.enabled:true}") boolean detailWarmupEnabled,
            @Value("${app.prediction.warmup.vote-status.enabled:true}") boolean voteStatusWarmupEnabled) {
        this(
                predictionService,
                Clock.systemDefaultZone(),
                enabled,
                detailWarmupEnabled,
                voteStatusWarmupEnabled);
    }

    PredictionWarmupService(
            PredictionService predictionService,
            Clock clock,
            boolean enabled,
            boolean detailWarmupEnabled,
            boolean voteStatusWarmupEnabled) {
        this.predictionService = predictionService;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.enabled = enabled;
        this.detailWarmupEnabled = detailWarmupEnabled;
        this.voteStatusWarmupEnabled = voteStatusWarmupEnabled;
    }

    @Scheduled(
            fixedDelayString = "${app.prediction.warmup.fixed-delay-ms:50000}",
            initialDelayString = "${app.prediction.warmup.initial-delay-ms:7000}")
    public void warmupTodayPredictionData() {
        if (!enabled) {
            return;
        }

        LocalDate today = LocalDate.now(clock);
        long startedAtNanos = System.nanoTime();
        try {
            MatchDayNavigationResponseDto schedule = predictionService.getMatchDayNavigation(today);
            List<MatchDto> games = schedule.getGames() == null ? List.of() : schedule.getGames();
            int detailWarmCount = 0;
            int voteStatusWarmCount = 0;

            for (MatchDto game : games) {
                String gameId = game == null ? null : game.getGameId();
                if (gameId == null || gameId.isBlank()) {
                    continue;
                }

                if (detailWarmupEnabled && warmDetail(gameId)) {
                    detailWarmCount++;
                }
                if (voteStatusWarmupEnabled && warmVoteStatus(gameId)) {
                    voteStatusWarmCount++;
                }
            }

            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
            log.info(
                    "prediction.warmup.completed date={} matchCount={} detailWarmCount={} voteStatusWarmCount={} elapsedMs={}",
                    today,
                    games.size(),
                    detailWarmCount,
                    voteStatusWarmCount,
                    elapsedMs);
        } catch (RuntimeException ex) {
            log.warn("prediction.warmup.failed date={} reason={}", today, ex.getMessage());
        }
    }

    private boolean warmDetail(String gameId) {
        try {
            predictionService.getGameDetail(gameId);
            return true;
        } catch (RuntimeException ex) {
            log.debug("prediction.warmup.detail_failed gameId={} reason={}", gameId, ex.getMessage());
            return false;
        }
    }

    private boolean warmVoteStatus(String gameId) {
        try {
            predictionService.getVoteStatus(gameId);
            return true;
        } catch (RuntimeException ex) {
            log.debug("prediction.warmup.vote_status_failed gameId={} reason={}", gameId, ex.getMessage());
            return false;
        }
    }
}
