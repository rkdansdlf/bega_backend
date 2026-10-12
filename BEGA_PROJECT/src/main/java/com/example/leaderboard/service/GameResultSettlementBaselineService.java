package com.example.leaderboard.service;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.repository.GameRepository;
import com.example.kbo.util.KboTeamCodePolicy;
import com.example.leaderboard.entity.GameResultSettlementBaseline;
import com.example.leaderboard.entity.LegacyPerfectDayDate;
import com.example.leaderboard.repository.GameResultSettlementBaselineRepository;
import com.example.leaderboard.repository.LegacyPerfectDayDateRepository;
import com.example.prediction.Prediction;
import com.example.prediction.PredictionRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameResultSettlementBaselineService {

    private static final int MAX_BATCHES_PER_RUN = 20;
    private static final int BASELINE_BATCH_SIZE = 500;
    private static final int PENDING_GAME_BATCH_SIZE = 200;

    private final GameResultSettlementBaselineRepository baselineRepository;
    private final LegacyPerfectDayDateRepository legacyPerfectDayDateRepository;
    private final PredictionRepository predictionRepository;
    private final GameRepository gameRepository;
    private final PlatformTransactionManager transactionManager;

    public boolean prepareForScoring() {
        TransactionTemplate batchTransaction = new TransactionTemplate(transactionManager);
        batchTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
            Boolean complete = batchTransaction.execute(status -> processNextBatchInTransaction());
            if (Boolean.TRUE.equals(complete)) {
                return true;
            }
        }
        return isComplete();
    }

    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public boolean isComplete() {
        return baselineRepository.findById(GameResultSettlementBaseline.SINGLETON_ID)
                .map(GameResultSettlementBaseline::isComplete)
                .orElse(false);
    }

    @Transactional(
            transactionManager = "transactionManager",
            propagation = Propagation.REQUIRES_NEW)
    public List<String> reserveNextPendingGameBatch() {
        GameResultSettlementBaseline baseline = baselineRepository
                .findByIdForUpdate(GameResultSettlementBaseline.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("Game result settlement baseline is missing"));
        if (!baseline.isComplete()) {
            return List.of();
        }

        List<String> gameIds = predictionRepository.findDistinctPendingGameIdsAfter(
                baseline.getPendingGameCursor(),
                Prediction.ScoreSettlementStatus.PENDING,
                PageRequest.of(0, PENDING_GAME_BATCH_SIZE));
        if (gameIds.isEmpty()) {
            baseline.advancePendingGameCursor(null);
        } else {
            baseline.advancePendingGameCursor(gameIds.get(gameIds.size() - 1));
        }
        baselineRepository.save(baseline);
        return gameIds;
    }

    @Transactional(
            transactionManager = "transactionManager",
            propagation = Propagation.REQUIRES_NEW)
    public boolean processNextBatch() {
        return processNextBatchInTransaction();
    }

    private boolean processNextBatchInTransaction() {
        GameResultSettlementBaseline baseline = baselineRepository
                .findByIdForUpdate(GameResultSettlementBaseline.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("Game result settlement baseline is missing"));
        if (baseline.isComplete()) {
            return true;
        }

        List<Prediction> existingPredictions = predictionRepository
                .findTop500ByIdGreaterThanAndIdLessThanEqualOrderByIdAsc(
                        baseline.getLastPredictionId(), baseline.getCutoffPredictionId());
        if (existingPredictions.isEmpty()) {
            baseline.advanceTo(baseline.getLastPredictionId(), true);
            baselineRepository.save(baseline);
            return true;
        }

        Set<String> gameIds = new HashSet<>();
        for (Prediction prediction : existingPredictions) {
            gameIds.add(prediction.getGameId());
        }

        Map<String, GameEntity> gamesById = new HashMap<>();
        if (!gameIds.isEmpty()) {
            for (GameEntity game : gameRepository.findByGameIdIn(gameIds)) {
                gamesById.put(game.getGameId(), game);
            }
        }

        Set<Long> pendingPredictionIds = new HashSet<>();
        Set<Long> legacyPredictionIds = new HashSet<>();
        Set<LegacyPerfectDayDate> frozenDates = new HashSet<>();
        for (Prediction prediction : existingPredictions) {
            GameEntity game = gamesById.get(prediction.getGameId());
            if (game == null) {
                log.warn(
                        "MANUAL_BASEBALL_DATA_REQUIRED: cannot classify legacy prediction {} because internal game {} is missing",
                        prediction.getId(),
                        prediction.getGameId());
                pendingPredictionIds.add(prediction.getId());
                continue;
            }

            if (game.isFinished()) {
                if (!game.isDummyGame() && game.getGameDate() != null) {
                    frozenDates.add(new LegacyPerfectDayDate(game.getGameDate()));
                }
                if (!isCanonicalGame(game)) {
                    log.warn(
                            "MANUAL_BASEBALL_DATA_REQUIRED: finished legacy game {} has incomplete scoring identity",
                            game.getGameId());
                }
                legacyPredictionIds.add(prediction.getId());
                continue;
            }

            if (isScorableGame(game)) {
                pendingPredictionIds.add(prediction.getId());
            } else if (game.isDummyGame()) {
                legacyPredictionIds.add(prediction.getId());
            } else {
                log.warn(
                        "MANUAL_BASEBALL_DATA_REQUIRED: legacy prediction {} references non-canonical internal game {}",
                        prediction.getId(),
                        game.getGameId());
                legacyPredictionIds.add(prediction.getId());
            }
        }

        if (!pendingPredictionIds.isEmpty()) {
            predictionRepository.markPendingForSettlement(
                    pendingPredictionIds,
                    Prediction.ScoreSettlementStatus.PENDING);
        }
        if (!legacyPredictionIds.isEmpty()) {
            predictionRepository.markLegacyForSettlement(
                    legacyPredictionIds,
                    Prediction.ScoreSettlementStatus.LEGACY);
        }
        if (!frozenDates.isEmpty()) {
            legacyPerfectDayDateRepository.saveAll(frozenDates);
        }

        Long lastProcessedId = existingPredictions.get(existingPredictions.size() - 1).getId();
        boolean complete = existingPredictions.size() < BASELINE_BATCH_SIZE;
        baseline.advanceTo(lastProcessedId, complete);
        baselineRepository.save(baseline);
        return complete;
    }

    private boolean isCanonicalGame(GameEntity game) {
        return KboTeamCodePolicy.isCanonicalTeamCode(game.getHomeTeam())
                && KboTeamCodePolicy.isCanonicalTeamCode(game.getAwayTeam());
    }

    private boolean isScorableGame(GameEntity game) {
        return !game.isDummyGame() && isCanonicalGame(game);
    }
}
