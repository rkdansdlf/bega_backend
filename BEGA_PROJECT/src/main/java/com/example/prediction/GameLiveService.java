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
import com.example.kbo.repository.GameDetailHeaderProjection;
import com.example.kbo.repository.GameEventRepository;
import com.example.kbo.repository.GameRepository;
import com.example.kbo.util.GameStatusResolver;
import com.example.kbo.validation.BaseballDataIntegrityGuard;
import com.example.kbo.validation.ManualBaseballDataMissingItem;
import com.example.kbo.validation.ManualBaseballDataRequest;
import com.example.kbo.validation.ManualBaseballDataRequiredException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GameLiveService {

    private static final int DEFAULT_EVENT_LIMIT = 50;
    private static final int MAX_EVENT_LIMIT = 200;
    private static final String MATCH_NOT_FOUND_CODE = "MATCH_NOT_FOUND";

    private final GameRepository gameRepository;
    private final GameEventRepository gameEventRepository;
    private final BaseballDataIntegrityGuard baseballDataIntegrityGuard;

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
        ensureLiveEventsIfRequired("prediction.live_snapshot.events", game, latestEvent, header.getStartTime());

        String gameStatus = resolveLiveStatus(game, latestEvent, header.getStartTime());
        Integer homeScore = resolveHomeScore(game, latestEvent);
        Integer awayScore = resolveAwayScore(game, latestEvent);

        return GameLiveSnapshotDto.builder()
                .gameId(gameId)
                .gameStatus(gameStatus)
                .homeScore(homeScore)
                .awayScore(awayScore)
                .currentInning(latestEvent == null ? null : latestEvent.getInning())
                .currentInningHalf(latestEvent == null ? null : latestEvent.getInningHalf())
                .lastEventSeq(latestEvent == null ? null : latestEvent.getEventSeq())
                .lastUpdatedAt(resolveUpdatedAt(latestEvent))
                .events(events.stream()
                        .map(GameLiveEventDto::fromEntity)
                        .filter(Objects::nonNull)
                        .toList())
                .build();
    }

    @Transactional(readOnly = true, transactionManager = "kboGameTransactionManager")
    public List<GameLiveSummaryDto> getLiveSummaries(List<String> gameIds) {
        if (gameIds == null || gameIds.isEmpty()) {
            return List.of();
        }

        List<GameEntity> games = gameIds.stream()
                .map(gameId -> baseballDataIntegrityGuard.requireValidGame("prediction.live_summary", gameId))
                .toList();
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
                    ensureLiveEventsIfRequired("prediction.live_summary.events", game, latestEvent, null);
                    return GameLiveSummaryDto.builder()
                            .gameId(game.getGameId())
                            .gameStatus(resolveLiveStatus(game, latestEvent, null))
                            .homeScore(resolveHomeScore(game, latestEvent))
                            .awayScore(resolveAwayScore(game, latestEvent))
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

    private String resolveLiveStatus(GameEntity game, GameEventEntity latestEvent, LocalTime startTime) {
        Integer homeScore = resolveHomeScore(game, latestEvent);
        Integer awayScore = resolveAwayScore(game, latestEvent);
        boolean hasProgressData = latestEvent != null || (homeScore != null && awayScore != null);
        return GameStatusResolver.resolveEffectiveStatus(
                game.getGameStatus(),
                game.getGameDate(),
                startTime,
                homeScore,
                awayScore,
                hasProgressData);
    }

    private Integer resolveHomeScore(GameEntity game, GameEventEntity latestEvent) {
        return latestEvent != null && latestEvent.getHomeScore() != null
                ? latestEvent.getHomeScore()
                : game.getHomeScore();
    }

    private Integer resolveAwayScore(GameEntity game, GameEventEntity latestEvent) {
        return latestEvent != null && latestEvent.getAwayScore() != null
                ? latestEvent.getAwayScore()
                : game.getAwayScore();
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
            LocalTime startTime) {
        if (latestEvent != null) {
            return;
        }

        String liveStatus = resolveLiveStatus(game, null, startTime);
        if (!requiresLiveEvents(liveStatus)) {
            return;
        }

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
}
