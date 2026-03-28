package com.example.leaderboard.service;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.repository.GameRepository;
import com.example.kbo.util.KboTeamCodePolicy;
import com.example.leaderboard.dto.ScoreResultDto;
import com.example.leaderboard.repository.ScoreEventRepository;
import com.example.prediction.Prediction;
import com.example.prediction.PredictionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * 게임 결과 점수 처리 서비스
 * 종료된 게임의 예측 결과를 처리하고 점수를 부여합니다.
 *
 * 트랜잭션 정책:
 * - 점수/예측 결과 반영은 primary(transactionManager) 기준으로 처리
 * - 경기 조회(GameRepository)는 kboGameTransactionManager 경로의 read 전용 호출
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GameResultScoringService {

    private final GameRepository gameRepository;
    private final PredictionRepository predictionRepository;
    private final ScoreEventRepository scoreEventRepository;
    private final ScoringService scoringService;

    /**
     * 특정 게임의 모든 예측 결과 처리
     * 게임 종료 시 호출됩니다.
     *
     * @param gameId 게임 ID
     * @return 처리된 예측 수
     */
    @Transactional(transactionManager = "transactionManager")
    public int processGameResult(String gameId) {
        // 게임 조회
        GameEntity game = gameRepository.findByGameId(gameId)
                .orElseThrow(() -> new IllegalArgumentException("게임을 찾을 수 없습니다: " + gameId));

        return processGameResult(game, true);
    }

    /**
     * 특정 날짜의 종료된 모든 게임 처리
     * 스케줄러에서 호출됩니다.
     *
     * @param date 처리할 날짜
     * @return 처리된 게임 수
     */
    @Transactional(transactionManager = "transactionManager")
    public int processGamesForDate(LocalDate date) {
        List<GameEntity> finishedGames = gameRepository.findByGameDate(date).stream()
                .filter(GameEntity::isFinished)
                .filter(this::isCanonicalGame)
                .toList();

        if (finishedGames.isEmpty()) {
            log.info("No finished games found for date {}.", date);
            return 0;
        }

        int totalProcessed = 0;
        for (GameEntity game : finishedGames) {
            try {
                int processed = processGameResult(game, false);
                totalProcessed += processed;
            } catch (Exception e) {
                log.error("Failed to process game {}: {}", game.getGameId(), e.getMessage());
            }
        }

        log.info("Processed {} finished games for date {}.", finishedGames.size(), date);
        checkPerfectDay(finishedGames);
        return totalProcessed;
    }

    private int processGameResult(GameEntity game, boolean runPerfectDayCheck) {
        if (!isCanonicalGame(game)) {
            log.info("Skipping non-canonical game {} for scoring.", game.getGameId());
            return 0;
        }

        if (!game.isFinished()) {
            log.warn("Game {} is not finished yet. Skipping score processing.", game.getGameId());
            return 0;
        }

        String winner = game.getWinner(); // "home", "away", or "draw"
        if (winner == null || winner.isBlank()) {
            log.warn("Game {} has no winner information. Skipping score processing.", game.getGameId());
            return 0;
        }

        // 이변 여부 확인 (약팀이 이긴 경우)
        boolean isUpset = isUpsetGame(game);

        // 해당 게임의 모든 예측 조회
        List<Prediction> predictions = predictionRepository.findByGameId(game.getGameId());

        if (predictions.isEmpty()) {
            log.info("No predictions found for game {}.", game.getGameId());
            return 0;
        }

        Set<Long> processedPredictionIds = loadProcessedPredictionIds(
                predictions.stream().map(Prediction::getId).toList());

        int processedCount = 0;
        for (Prediction prediction : predictions) {
            // 이미 점수가 처리되었는지 확인
            if (isAlreadyProcessed(prediction.getId(), processedPredictionIds)) {
                log.debug("Prediction {} already processed for user {}.", prediction.getId(), prediction.getUserId());
                continue;
            }

            boolean isCorrect = prediction.getVotedTeam().equalsIgnoreCase(winner);

            try {
                ScoreResultDto result = scoringService.processPredictionResult(
                        prediction.getUserId(),
                        prediction.getId(),
                        game.getGameId(),
                        isCorrect,
                        isCorrect && isUpset);

                if (Boolean.TRUE.equals(result.getCorrect())) {
                    log.info("User {} earned {} points for game {} (streak: {})",
                            prediction.getUserId(), result.getTotalEarned(), game.getGameId(), result.getCurrentStreak());
                }

                processedCount++;
            } catch (Exception e) {
                log.error("Failed to process prediction {} for user {}: {}",
                        prediction.getId(), prediction.getUserId(), e.getMessage());
            }
        }

        log.info("Processed {} predictions for game {}.", processedCount, game.getGameId());

        if (runPerfectDayCheck) {
            checkPerfectDay(gameRepository.findByGameDate(game.getGameDate()).stream()
                    .filter(g -> !g.isDummyGame())
                    .filter(GameEntity::isFinished)
                    .filter(this::isCanonicalGame)
                    .toList());
        }

        return processedCount;
    }

    /**
     * 퍼펙트 데이 체크
     * 하루의 모든 경기를 맞힌 사용자에게 보너스 부여
     */
    private void checkPerfectDay(List<GameEntity> gamesForDate) {
        if (gamesForDate == null || gamesForDate.isEmpty()) {
            return;
        }

        int totalGames = gamesForDate.size();
        if (totalGames < 3) {
            return;
        }

        List<String> gameIds = gamesForDate.stream()
                .map(GameEntity::getGameId)
                .toList();
        List<Prediction> predictions = predictionRepository.findByGameIdIn(gameIds);
        if (predictions.isEmpty()) {
            return;
        }

        Map<String, String> winnerByGameId = gamesForDate.stream()
                .filter(game -> game.getWinner() != null && !game.getWinner().isBlank())
                .collect(java.util.stream.Collectors.toMap(
                        GameEntity::getGameId,
                        GameEntity::getWinner,
                        (left, right) -> left));

        Set<Long> allUsers = new HashSet<>();
        Map<Long, Integer> userCorrectCount = new HashMap<>();
        Set<String> gameIdSet = new HashSet<>(gameIds);

        for (Prediction prediction : predictions) {
            if (!gameIdSet.contains(prediction.getGameId())) {
                continue;
            }
            String winner = winnerByGameId.get(prediction.getGameId());
            if (winner == null || winner.isBlank()) {
                continue;
            }
            allUsers.add(prediction.getUserId());

            if (prediction.getVotedTeam().equalsIgnoreCase(winner)) {
                userCorrectCount.merge(prediction.getUserId(), 1, Integer::sum);
            }
        }

        // 퍼펙트 데이 달성 사용자 확인
        for (Long userId : allUsers) {
            int correctCount = userCorrectCount.getOrDefault(userId, 0);
            if (correctCount == totalGames) {
                try {
                    scoringService.processPerfectDay(userId, totalGames);
                } catch (Exception e) {
                    log.error("Failed to process perfect day for user {}: {}", userId, e.getMessage());
                }
            }
        }
    }

    /**
     * 이변 게임인지 확인
     * 간단한 로직: 원정팀이 이기면 이변으로 처리 (추후 개선 가능)
     */
    private boolean isUpsetGame(GameEntity game) {
        // 실제로는 양 팀의 순위나 최근 성적을 비교해야 함
        // 현재는 단순히 away 팀이 이기면 이변으로 처리
        return "away".equalsIgnoreCase(game.getWinner());
    }

    /**
     * 이미 점수가 처리되었는지 확인
     */
    private Set<Long> loadProcessedPredictionIds(List<Long> predictionIds) {
        if (predictionIds == null || predictionIds.isEmpty()) {
            return Collections.emptySet();
        }

        return new HashSet<>(scoreEventRepository.findProcessedPredictionIdsByPredictionIdIn(predictionIds));
    }

    private boolean isAlreadyProcessed(Long predictionId, Set<Long> processedPredictionIds) {
        return processedPredictionIds.contains(predictionId);
    }

    private boolean isCanonicalGame(GameEntity game) {
        if (game == null) {
            return false;
        }
        return KboTeamCodePolicy.isCanonicalTeamCode(game.getHomeTeam())
                && KboTeamCodePolicy.isCanonicalTeamCode(game.getAwayTeam());
    }
}
