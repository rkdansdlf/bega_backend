package com.example.prediction;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.kbo.entity.GamePlayByPlayEntity;
import com.example.kbo.repository.GamePlayByPlayRepository;
import com.example.kbo.validation.BaseballDataIntegrityGuard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameLiveRelayService {

    private static final int DEFAULT_RELAY_LIMIT = 50;
    private static final int MAX_RELAY_LIMIT = 200;

    private final GamePlayByPlayRepository gamePlayByPlayRepository;
    private final BaseballDataIntegrityGuard baseballDataIntegrityGuard;
    private final PredictionLiveMetricsService predictionLiveMetricsService;

    @Transactional(readOnly = true, transactionManager = "kboGameTransactionManager")
    public GameRelaySnapshotDto getRelaySnapshot(String gameId, Integer afterId, Integer limit) {
        baseballDataIntegrityGuard.requireValidGame("prediction.live_relay", gameId);
        int normalizedLimit = normalizeLimit(limit);
        List<GamePlayByPlayEntity> events = loadRelayEvents(gameId, afterId, normalizedLimit);
        GamePlayByPlayEntity latestRelay = gamePlayByPlayRepository.findFirstByGameIdOrderByIdDesc(gameId)
                .orElse(null);
        String result = latestRelay == null && events.isEmpty() ? "empty" : "ok";
        predictionLiveMetricsService.recordLiveRelaySnapshot(result);
        log.debug(
                "prediction.live_relay.resolved gameId={} eventCount={} latestRelayId={} result={}",
                gameId,
                events.size(),
                latestRelay == null ? null : latestRelay.getId(),
                result);

        return GameRelaySnapshotDto.builder()
                .gameId(gameId)
                .lastRelayId(latestRelay == null ? null : latestRelay.getId())
                .lastUpdatedAt(resolveUpdatedAt(latestRelay))
                .events(events.stream()
                        .map(GameRelayEventDto::fromEntity)
                        .filter(Objects::nonNull)
                        .toList())
                .build();
    }

    private List<GamePlayByPlayEntity> loadRelayEvents(String gameId, Integer afterId, int limit) {
        if (afterId != null) {
            return gamePlayByPlayRepository.findByGameIdAndIdGreaterThanOrderByIdAsc(
                    gameId,
                    Math.max(-1, afterId),
                    PageRequest.of(0, limit));
        }

        return gamePlayByPlayRepository.findByGameIdOrderByIdDesc(gameId, PageRequest.of(0, limit))
                .stream()
                .sorted(Comparator.comparing(GamePlayByPlayEntity::getId))
                .toList();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_RELAY_LIMIT;
        }
        return Math.max(1, Math.min(MAX_RELAY_LIMIT, limit));
    }

    private LocalDateTime resolveUpdatedAt(GamePlayByPlayEntity event) {
        if (event == null) {
            return null;
        }
        return Optional.ofNullable(event.getUpdatedAt()).orElse(event.getCreatedAt());
    }
}
