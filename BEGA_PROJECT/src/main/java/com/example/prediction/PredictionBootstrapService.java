package com.example.prediction;

import com.example.common.exception.BusinessException;
import com.example.kbo.validation.ManualBaseballDataRequest;
import com.example.kbo.validation.ManualBaseballDataRequiredException;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PredictionBootstrapService {

    private static final Duration MANUAL_DATA_NEGATIVE_CACHE_TTL = Duration.ofSeconds(60);

    private final PredictionService predictionService;
    private final ExecutorService bootstrapExecutor;
    private final ConcurrentMap<String, CachedManualDataFailure> manualDataFailureCache = new ConcurrentHashMap<>();

    @Autowired
    public PredictionBootstrapService(PredictionService predictionService) {
        this(predictionService, Executors.newVirtualThreadPerTaskExecutor());
    }

    PredictionBootstrapService(PredictionService predictionService, ExecutorService bootstrapExecutor) {
        this.predictionService = predictionService;
        this.bootstrapExecutor = bootstrapExecutor;
    }

    public PredictionBootstrapResponseDto getBootstrap(LocalDate date, String gameId) {
        long startedAtNanos = System.nanoTime();
        String manualDataCacheKey = manualDataCacheKey(date);
        throwCachedManualDataException(manualDataCacheKey);
        MatchDayNavigationResponseDto schedule;
        try {
            schedule = predictionService.getMatchDayNavigation(date);
        } catch (ManualBaseballDataRequiredException e) {
            cacheManualDataException(manualDataCacheKey, e);
            throw e;
        }
        String selectedGameId = normalizeGameId(gameId);
        boolean selectedGameFound = selectedGameId != null && containsGameId(schedule.getGames(), selectedGameId);

        PredictionBootstrapResourceDto<GameDetailDto> detail = null;
        PredictionBootstrapResourceDto<PredictionResponseDto> voteStatus = null;

        if (selectedGameFound) {
            Future<PredictionBootstrapResourceDto<GameDetailDto>> detailFuture = bootstrapExecutor.submit(
                    () -> loadGameDetail(selectedGameId));
            Future<PredictionBootstrapResourceDto<PredictionResponseDto>> voteStatusFuture = bootstrapExecutor.submit(
                    () -> loadVoteStatus(selectedGameId));

            detail = awaitResource(detailFuture, "detail");
            voteStatus = awaitResource(voteStatusFuture, "voteStatus");
        }

        logBootstrapElapsed(startedAtNanos, date, selectedGameId, schedule, selectedGameFound, detail, voteStatus);
        return new PredictionBootstrapResponseDto(
                schedule,
                selectedGameId,
                selectedGameFound,
                detail,
                voteStatus);
    }

    @PreDestroy
    void shutdown() {
        bootstrapExecutor.shutdown();
    }

    private PredictionBootstrapResourceDto<GameDetailDto> loadGameDetail(String gameId) {
        try {
            return PredictionBootstrapResourceDto.success(predictionService.getGameDetail(gameId));
        } catch (RuntimeException ex) {
            return PredictionBootstrapResourceDto.failure(toErrorDto(ex));
        }
    }

    private PredictionBootstrapResourceDto<PredictionResponseDto> loadVoteStatus(String gameId) {
        try {
            return PredictionBootstrapResourceDto.success(predictionService.getVoteStatus(gameId));
        } catch (RuntimeException ex) {
            return PredictionBootstrapResourceDto.failure(toErrorDto(ex));
        }
    }

    private <T> PredictionBootstrapResourceDto<T> awaitResource(
            Future<PredictionBootstrapResourceDto<T>> future,
            String resourceName) {
        try {
            return future.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return PredictionBootstrapResourceDto.failure(new PredictionBootstrapErrorDto(
                    resourceName + " 조회가 중단되었습니다.",
                    503,
                    "PREDICTION_BOOTSTRAP_INTERRUPTED"));
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                return PredictionBootstrapResourceDto.failure(toErrorDto(runtimeException));
            }
            return PredictionBootstrapResourceDto.failure(new PredictionBootstrapErrorDto(
                    resourceName + " 조회에 실패했습니다.",
                    500,
                    "PREDICTION_BOOTSTRAP_RESOURCE_FAILED"));
        }
    }

    private PredictionBootstrapErrorDto toErrorDto(RuntimeException ex) {
        if (ex instanceof BusinessException businessException) {
            return new PredictionBootstrapErrorDto(
                    businessException.getMessage(),
                    businessException.getStatus().value(),
                    businessException.getCode());
        }
        return new PredictionBootstrapErrorDto(
                "요청한 예측 데이터를 불러오지 못했습니다.",
                500,
                "PREDICTION_BOOTSTRAP_RESOURCE_FAILED");
    }

    private String normalizeGameId(String gameId) {
        if (gameId == null) {
            return null;
        }
        String normalized = gameId.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean containsGameId(List<MatchDto> games, String gameId) {
        if (games == null || games.isEmpty()) {
            return false;
        }
        return games.stream()
                .anyMatch(game -> game != null && gameId.equals(game.getGameId()));
    }

    private String manualDataCacheKey(LocalDate date) {
        return "predictionBootstrap:date:" + date;
    }

    private void throwCachedManualDataException(String cacheKey) {
        CachedManualDataFailure cached = manualDataFailureCache.get(cacheKey);
        if (cached == null) {
            return;
        }
        long nowNanos = System.nanoTime();
        if (nowNanos - cached.expiresAtNanos() >= 0) {
            manualDataFailureCache.remove(cacheKey, cached);
            return;
        }
        throw new ManualBaseballDataRequiredException(cached.request());
    }

    private void cacheManualDataException(String cacheKey, ManualBaseballDataRequiredException exception) {
        Object data = exception.getData();
        if (!(data instanceof ManualBaseballDataRequest request)) {
            return;
        }
        manualDataFailureCache.put(
                cacheKey,
                new CachedManualDataFailure(
                        request,
                        System.nanoTime() + MANUAL_DATA_NEGATIVE_CACHE_TTL.toNanos()));
    }

    private void logBootstrapElapsed(
            long startedAtNanos,
            LocalDate date,
            String gameId,
            MatchDayNavigationResponseDto schedule,
            boolean selectedGameFound,
            PredictionBootstrapResourceDto<GameDetailDto> detail,
            PredictionBootstrapResourceDto<PredictionResponseDto> voteStatus) {
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
        int matchCount = schedule.getGames() == null ? 0 : schedule.getGames().size();
        log.info(
                "prediction.bootstrap.load date={} gameId={} matchCount={} selectedGameFound={} detailOk={} voteStatusOk={} elapsedMs={}",
                date,
                gameId,
                matchCount,
                selectedGameFound,
                detail == null ? null : detail.ok(),
                voteStatus == null ? null : voteStatus.ok(),
                elapsedMs);
    }

    private record CachedManualDataFailure(ManualBaseballDataRequest request, long expiresAtNanos) {
    }
}
