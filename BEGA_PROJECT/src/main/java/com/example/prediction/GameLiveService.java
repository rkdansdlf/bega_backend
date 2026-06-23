package com.example.prediction;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.common.exception.NotFoundBusinessException;
import com.example.kbo.entity.GameEntity;
import com.example.kbo.entity.GameEventEntity;
import com.example.kbo.entity.GameInningScoreEntity;
import com.example.kbo.repository.GameDetailHeaderProjection;
import com.example.kbo.repository.GameEventRepository;
import com.example.kbo.repository.GameInningScoreRepository;
import com.example.kbo.repository.GameRepository;
import com.example.kbo.util.GameStatusResolver;
import com.example.kbo.validation.BaseballDataIntegrityGuard;
import com.example.kbo.validation.ManualBaseballDataMissingItem;
import com.example.kbo.validation.ManualBaseballDataRequest;
import com.example.kbo.validation.ManualBaseballDataRequiredException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameLiveService {

    private static final int DEFAULT_EVENT_LIMIT = 50;
    private static final int MAX_EVENT_LIMIT = 200;
    private static final String MATCH_NOT_FOUND_CODE = "MATCH_NOT_FOUND";

    private final GameRepository gameRepository;
    private final GameEventRepository gameEventRepository;
    private final GameInningScoreRepository gameInningScoreRepository;
    private final BaseballDataIntegrityGuard baseballDataIntegrityGuard;
    private final PredictionLiveMetricsService predictionLiveMetricsService;

    @Transactional(readOnly = true, transactionManager = "kboGameTransactionManager")
    public GameLiveSnapshotDto getLiveSnapshot(String gameId, Integer afterSeq, Integer limit) {
        GameEntity game = baseballDataIntegrityGuard.requireValidGame("prediction.live_snapshot", gameId);
        GameDetailHeaderProjection header = gameRepository.findGameDetailHeaderByGameId(gameId)
                .orElseThrow(() -> new NotFoundBusinessException(
                        MATCH_NOT_FOUND_CODE,
                        "경기 정보를 찾을 수 없습니다."));
        int normalizedLimit = normalizeLimit(limit);
        List<GameEventEntity> events = loadEvents(gameId, afterSeq, normalizedLimit);
        GameEventEntity latestEvent = gameEventRepository.findFirstByGameIdOrderByEventSeqDesc(gameId).orElse(null);
        List<GameInningScoreEntity> inningScores = loadMeaningfulInningScores(gameId, game);
        ensureLiveEventsIfRequired("prediction.live_snapshot.events", game, latestEvent, inningScores, header.getStartTime());

        String gameStatus = resolveLiveStatus(game, latestEvent, inningScores, header.getStartTime());
        Integer homeScore = resolveHomeScore(game, latestEvent, inningScores);
        Integer awayScore = resolveAwayScore(game, latestEvent, inningScores);
        String scoreSource = resolveScoreSource(game, latestEvent, inningScores);
        List<GameLiveEventDto> liveEvents = events.stream()
                .map(GameLiveEventDto::fromEntity)
                .filter(Objects::nonNull)
                .toList();
        List<GameInningScoreDto> liveInningScores = inningScores.stream()
                .map(GameInningScoreDto::fromEntity)
                .filter(Objects::nonNull)
                .toList();

        predictionLiveMetricsService.recordLiveSnapshot(scoreSource, !liveInningScores.isEmpty());
        log.debug(
                "prediction.live_snapshot.resolved gameId={} eventCount={} inningScoreCount={} scoreSource={} homeScore={} awayScore={} lastEventSeq={}",
                gameId,
                liveEvents.size(),
                liveInningScores.size(),
                scoreSource,
                homeScore,
                awayScore,
                latestEvent == null ? null : latestEvent.getEventSeq());

        return GameLiveSnapshotDto.builder()
                .gameId(gameId)
                .gameStatus(gameStatus)
                .homeScore(homeScore)
                .awayScore(awayScore)
                .currentInning(latestEvent == null ? null : latestEvent.getInning())
                .currentInningHalf(latestEvent == null ? null : latestEvent.getInningHalf())
                .lastEventSeq(latestEvent == null ? null : latestEvent.getEventSeq())
                .lastUpdatedAt(resolveUpdatedAt(latestEvent))
                .events(liveEvents)
                .inningScores(liveInningScores)
                .build();
    }

    @Transactional(readOnly = true, transactionManager = "kboGameTransactionManager")
    public List<GameLiveSummaryDto> getLiveSummaries(List<String> gameIds) {
        if (gameIds == null || gameIds.isEmpty()) {
            return List.of();
        }

        List<GameEntity> games = baseballDataIntegrityGuard.requireValidGames("prediction.live_summary", gameIds);
        Map<String, GameEventEntity> latestByGameId = new LinkedHashMap<>();
        gameEventRepository.findLatestByGameIds(gameIds).forEach(event -> {
            if (event == null || event.getGameId() == null) {
                return;
            }
            latestByGameId.merge(
                    event.getGameId(),
                    event,
                    (left, right) -> compareEventSeq(left, right) >= 0 ? left : right);
        });

        return games.stream()
                .map(game -> {
                    GameEventEntity latestEvent = latestByGameId.get(game.getGameId());
                    ensureLiveEventsIfRequired("prediction.live_summary.events", game, latestEvent, List.of(), null);
                    return GameLiveSummaryDto.builder()
                            .gameId(game.getGameId())
                            .gameStatus(resolveLiveStatus(game, latestEvent, List.of(), null))
                            .homeScore(resolveHomeScore(game, latestEvent, List.of()))
                            .awayScore(resolveAwayScore(game, latestEvent, List.of()))
                            .lastEventSeq(latestEvent == null ? null : latestEvent.getEventSeq())
                            .lastUpdatedAt(resolveUpdatedAt(latestEvent))
                            .build();
                })
                .toList();
    }

    private List<GameEventEntity> loadEvents(String gameId, Integer afterSeq, int limit) {
        if (afterSeq != null) {
            return gameEventRepository.findByGameIdAndEventSeqGreaterThanOrderByEventSeqAsc(
                    gameId,
                    Math.max(-1, afterSeq),
                    PageRequest.of(0, limit));
        }

        return gameEventRepository.findByGameIdOrderByEventSeqDesc(gameId, PageRequest.of(0, limit))
                .stream()
                .sorted(Comparator.comparing(GameEventEntity::getEventSeq))
                .toList();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_EVENT_LIMIT;
        }
        return Math.max(1, Math.min(MAX_EVENT_LIMIT, limit));
    }

    private String resolveLiveStatus(
            GameEntity game,
            GameEventEntity latestEvent,
            List<GameInningScoreEntity> inningScores,
            LocalTime startTime) {
        Integer homeScore = resolveHomeScore(game, latestEvent, inningScores);
        Integer awayScore = resolveAwayScore(game, latestEvent, inningScores);
        boolean hasProgressData = latestEvent != null
                || !isEmpty(inningScores)
                || (homeScore != null && awayScore != null);
        return GameStatusResolver.resolveEffectiveStatus(
                game.getGameStatus(),
                game.getGameDate(),
                startTime,
                homeScore,
                awayScore,
                hasProgressData);
    }

    private Integer resolveHomeScore(
            GameEntity game,
            GameEventEntity latestEvent,
            List<GameInningScoreEntity> inningScores) {
        if (latestEvent != null && latestEvent.getHomeScore() != null) {
            return latestEvent.getHomeScore();
        }
        Integer inningScore = sumInningRuns(inningScores, "home");
        return inningScore != null ? inningScore : game.getHomeScore();
    }

    private Integer resolveAwayScore(
            GameEntity game,
            GameEventEntity latestEvent,
            List<GameInningScoreEntity> inningScores) {
        if (latestEvent != null && latestEvent.getAwayScore() != null) {
            return latestEvent.getAwayScore();
        }
        Integer inningScore = sumInningRuns(inningScores, "away");
        return inningScore != null ? inningScore : game.getAwayScore();
    }

    private String resolveScoreSource(
            GameEntity game,
            GameEventEntity latestEvent,
            List<GameInningScoreEntity> inningScores) {
        String homeSource = resolveSideScoreSource(
                latestEvent == null ? null : latestEvent.getHomeScore(),
                sumInningRuns(inningScores, "home"),
                game.getHomeScore());
        String awaySource = resolveSideScoreSource(
                latestEvent == null ? null : latestEvent.getAwayScore(),
                sumInningRuns(inningScores, "away"),
                game.getAwayScore());
        if (homeSource.equals(awaySource)) {
            return homeSource;
        }
        return "mixed";
    }

    private String resolveSideScoreSource(Integer eventScore, Integer inningScore, Integer gameScore) {
        if (eventScore != null) {
            return "game_events";
        }
        if (inningScore != null) {
            return "inning_scores";
        }
        if (gameScore != null) {
            return "game_entity";
        }
        return "none";
    }

    private LocalDateTime resolveUpdatedAt(GameEventEntity event) {
        if (event == null) {
            return null;
        }
        return Optional.ofNullable(event.getUpdatedAt()).orElse(event.getCreatedAt());
    }

    private int compareEventSeq(GameEventEntity left, GameEventEntity right) {
        Integer leftSeq = left == null ? null : left.getEventSeq();
        Integer rightSeq = right == null ? null : right.getEventSeq();
        if (leftSeq == null && rightSeq == null) {
            return 0;
        }
        if (leftSeq == null) {
            return -1;
        }
        if (rightSeq == null) {
            return 1;
        }
        return leftSeq.compareTo(rightSeq);
    }

    private void ensureLiveEventsIfRequired(
            String scope,
            GameEntity game,
            GameEventEntity latestEvent,
            List<GameInningScoreEntity> inningScores,
            LocalTime startTime) {
        if (latestEvent != null || !isEmpty(inningScores)) {
            return;
        }

        String liveStatus = resolveLiveStatus(game, null, List.of(), startTime);
        if (!requiresLiveEvents(liveStatus)) {
            return;
        }

        predictionLiveMetricsService.recordManualRequired("score_events");
        log.warn(
                "prediction.live_snapshot.manual_required gameId={} status={} scope={} reason=no_game_events_or_inning_scores",
                game.getGameId(),
                liveStatus,
                scope);
        throw new ManualBaseballDataRequiredException(
                new ManualBaseballDataRequest(
                        scope,
                        List.of(new ManualBaseballDataMissingItem(
                                "game_events",
                                "문자중계 이벤트",
                                "진행 또는 종료 경기의 문자중계 event row가 없습니다.",
                                "game_events.game_id, event_seq, inning, description, home_score, away_score")),
                        buildOperatorMessage(game),
                        true));
    }

    private boolean requiresLiveEvents(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        return "LIVE".equals(normalized)
                || "IN_PROGRESS".equals(normalized)
                || "INPROGRESS".equals(normalized)
                || "PLAYING".equals(normalized)
                || "COMPLETED".equals(normalized)
                || "DRAW".equals(normalized)
                || "FINAL".equals(normalized);
    }

    private String buildOperatorMessage(GameEntity game) {
        String gameId = game == null ? null : game.getGameId();
        String gameDate = game == null || game.getGameDate() == null ? null : game.getGameDate().toString();
        String target = gameId == null || gameId.isBlank() ? "" : "경기 ID=" + gameId + ", ";
        String date = gameDate == null ? "" : "날짜=" + gameDate + ", ";
        return "다음 야구 데이터가 필요합니다: " + target + date + "문자중계 이벤트(진행/종료 경기의 game_events row가 필요합니다.)";
    }

    private List<GameInningScoreEntity> loadMeaningfulInningScores(String gameId, GameEntity game) {
        List<GameInningScoreEntity> rawInningScores = gameInningScoreRepository
                .findAllByGameIdOrderByInningAscTeamSideAsc(gameId);
        return GameInningScoreSupport.normalizeMeaningful(
                rawInningScores,
                game.getHomeScore(),
                game.getAwayScore());
    }

    private Integer sumInningRuns(List<GameInningScoreEntity> inningScores, String teamSide) {
        if (isEmpty(inningScores)) {
            return null;
        }
        return inningScores.stream()
                .filter(score -> score != null && score.getRuns() != null)
                .filter(score -> teamSide.equalsIgnoreCase(score.getTeamSide()))
                .mapToInt(GameInningScoreEntity::getRuns)
                .sum();
    }

    private boolean isEmpty(List<?> values) {
        return values == null || values.isEmpty();
    }
}
