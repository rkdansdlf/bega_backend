package com.example.prediction;

import com.example.common.cache.BoundedLocalCache;
import com.example.common.exception.BusinessException;
import com.example.kbo.validation.ManualBaseballDataRequest;
import com.example.kbo.validation.ManualBaseballDataRequiredException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PredictionBootstrapService {

    private static final Duration MANUAL_DATA_NEGATIVE_CACHE_TTL = Duration.ofSeconds(60);
    private static final Duration DEFAULT_RESOURCE_TIMEOUT = Duration.ofMillis(1200);
    private static final int MANUAL_DATA_CACHE_MAX_ENTRIES = 256;
    private static final int DEFAULT_RESOURCE_MAX_CONCURRENCY = 16;
    private static final Duration DEFAULT_RESOURCE_PERMIT_WAIT_TIMEOUT = Duration.ofMillis(50);
    private static final String BOOTSTRAP_EVENT_METRIC = "prediction_bootstrap_events_total";
    private static final String RESOURCE_DURATION_METRIC = "prediction_bootstrap_resource_duration_seconds";

    private final PredictionService predictionService;
    private final ExecutorService bootstrapExecutor;
    private final Duration resourceTimeout;
    private final MeterRegistry meterRegistry;
    private final Semaphore resourceBulkhead;
    private final int resourceMaxConcurrency;
    private final Duration resourcePermitWaitTimeout;
    private final BoundedLocalCache<String, CachedManualDataFailure> manualDataFailureCache =
            new BoundedLocalCache<>(MANUAL_DATA_CACHE_MAX_ENTRIES);
    private final ConcurrentMap<String, CompletableFuture<PredictionBootstrapResponseDto>> inFlightBootstrapRequests =
            new ConcurrentHashMap<>();

    @Autowired
    public PredictionBootstrapService(
            PredictionService predictionService,
            @Value("${app.prediction.bootstrap.resource-timeout-ms:1200}") long resourceTimeoutMs,
            @Value("${app.prediction.bootstrap.resource-max-concurrency:16}") int resourceMaxConcurrency,
            @Value("${app.prediction.bootstrap.resource-permit-wait-timeout-ms:50}") long resourcePermitWaitTimeoutMs,
            MeterRegistry meterRegistry) {
        this(
                predictionService,
                Executors.newVirtualThreadPerTaskExecutor(),
                Duration.ofMillis(resourceTimeoutMs),
                meterRegistry,
                resourceMaxConcurrency,
                Duration.ofMillis(resourcePermitWaitTimeoutMs));
    }

    PredictionBootstrapService(PredictionService predictionService, ExecutorService bootstrapExecutor) {
        this(predictionService, bootstrapExecutor, DEFAULT_RESOURCE_TIMEOUT);
    }

    PredictionBootstrapService(
            PredictionService predictionService,
            ExecutorService bootstrapExecutor,
            Duration resourceTimeout) {
        this(predictionService, bootstrapExecutor, resourceTimeout, Metrics.globalRegistry);
    }

    PredictionBootstrapService(
            PredictionService predictionService,
            ExecutorService bootstrapExecutor,
            Duration resourceTimeout,
            MeterRegistry meterRegistry) {
        this(
                predictionService,
                bootstrapExecutor,
                resourceTimeout,
                meterRegistry,
                DEFAULT_RESOURCE_MAX_CONCURRENCY,
                DEFAULT_RESOURCE_PERMIT_WAIT_TIMEOUT);
    }

    PredictionBootstrapService(
            PredictionService predictionService,
            ExecutorService bootstrapExecutor,
            Duration resourceTimeout,
            MeterRegistry meterRegistry,
            int resourceMaxConcurrency,
            Duration resourcePermitWaitTimeout) {
        this.predictionService = predictionService;
        this.bootstrapExecutor = bootstrapExecutor;
        this.resourceTimeout = normalizeResourceTimeout(resourceTimeout);
        this.meterRegistry = meterRegistry == null ? Metrics.globalRegistry : meterRegistry;
        this.resourceMaxConcurrency = Math.max(1, resourceMaxConcurrency);
        this.resourcePermitWaitTimeout = normalizePermitWaitTimeout(resourcePermitWaitTimeout);
        this.resourceBulkhead = new Semaphore(this.resourceMaxConcurrency);
        registerResourceBulkheadMetrics();
    }

    public PredictionBootstrapResponseDto getBootstrap(LocalDate date, String gameId) {
        String selectedGameId = normalizeGameId(gameId);
        String requestKey = bootstrapRequestKey(date, selectedGameId);
        CompletableFuture<PredictionBootstrapResponseDto> newRequest = new CompletableFuture<>();
        CompletableFuture<PredictionBootstrapResponseDto> existingRequest =
                inFlightBootstrapRequests.putIfAbsent(requestKey, newRequest);
        if (existingRequest != null) {
            recordBootstrapEvent("inflight", "hit");
            return awaitInFlightBootstrap(existingRequest);
        }
        recordBootstrapEvent("inflight", "miss");

        try {
            PredictionBootstrapResponseDto response = loadBootstrap(date, selectedGameId);
            newRequest.complete(response);
            return response;
        } catch (RuntimeException | Error exception) {
            newRequest.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlightBootstrapRequests.remove(requestKey, newRequest);
        }
    }

    private PredictionBootstrapResponseDto loadBootstrap(LocalDate date, String selectedGameId) {
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
        boolean selectedGameFound = selectedGameId != null && containsGameId(schedule.getGames(), selectedGameId);

        PredictionBootstrapResourceDto<GameDetailDto> detail = null;
        PredictionBootstrapResourceDto<PredictionResponseDto> voteStatus = null;

        if (selectedGameFound) {
            long resourceStartedAtNanos = System.nanoTime();
            Future<TimedResource<GameDetailDto>> detailFuture = bootstrapExecutor.submit(
                    () -> loadTimedResourceWithPermit("detail", () -> loadGameDetail(selectedGameId)));
            Future<TimedResource<PredictionResponseDto>> voteStatusFuture = bootstrapExecutor.submit(
                    () -> loadTimedResourceWithPermit("voteStatus", () -> loadVoteStatus(selectedGameId)));
            long resourceDeadlineNanos = resourceStartedAtNanos + resourceTimeout.toNanos();

            detail = awaitResource(detailFuture, "detail", resourceDeadlineNanos, resourceStartedAtNanos);
            voteStatus = awaitResource(voteStatusFuture, "voteStatus", resourceDeadlineNanos, resourceStartedAtNanos);
        }

        logBootstrapElapsed(startedAtNanos, date, selectedGameId, schedule, selectedGameFound, detail, voteStatus);
        return new PredictionBootstrapResponseDto(
                schedule,
                selectedGameId,
                selectedGameFound,
                detail,
                voteStatus);
    }

    private PredictionBootstrapResponseDto awaitInFlightBootstrap(
            CompletableFuture<PredictionBootstrapResponseDto> inFlightRequest) {
        try {
            return inFlightRequest.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Prediction bootstrap in-flight request failed.", cause);
        }
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
            Future<TimedResource<T>> future,
            String resourceName,
            long deadlineNanos,
            long waitStartedAtNanos) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            if (future.isDone()) {
                return awaitCompletedResource(future, resourceName, waitStartedAtNanos);
            }
            return timeoutResource(future, resourceName, waitStartedAtNanos);
        }

        try {
            TimedResource<T> timedResource = future.get(remainingNanos, TimeUnit.NANOSECONDS);
            recordResourceDuration(
                    resourceName,
                    classifyResourceResult(timedResource.resource()),
                    timedResource.durationNanos());
            return timedResource.resource();
        } catch (TimeoutException ex) {
            return timeoutResource(future, resourceName, waitStartedAtNanos);
        } catch (InterruptedException ex) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            PredictionBootstrapResourceDto<T> resource = PredictionBootstrapResourceDto.failure(new PredictionBootstrapErrorDto(
                    resourceName + " 조회가 중단되었습니다.",
                    503,
                    "PREDICTION_BOOTSTRAP_INTERRUPTED"));
            recordResourceDuration(resourceName, "interrupted", elapsedNanos(waitStartedAtNanos));
            return resource;
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                PredictionBootstrapResourceDto<T> resource = PredictionBootstrapResourceDto.failure(
                        toErrorDto(runtimeException));
                recordResourceDuration(resourceName, "failure", elapsedNanos(waitStartedAtNanos));
                return resource;
            }
            PredictionBootstrapResourceDto<T> resource = PredictionBootstrapResourceDto.failure(new PredictionBootstrapErrorDto(
                    resourceName + " 조회에 실패했습니다.",
                    500,
                    "PREDICTION_BOOTSTRAP_RESOURCE_FAILED"));
            recordResourceDuration(resourceName, "failure", elapsedNanos(waitStartedAtNanos));
            return resource;
        }
    }

    private <T> PredictionBootstrapResourceDto<T> awaitCompletedResource(
            Future<TimedResource<T>> future,
            String resourceName,
            long waitStartedAtNanos) {
        try {
            TimedResource<T> timedResource = future.get();
            recordResourceDuration(
                    resourceName,
                    classifyResourceResult(timedResource.resource()),
                    timedResource.durationNanos());
            return timedResource.resource();
        } catch (InterruptedException ex) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            PredictionBootstrapResourceDto<T> resource = PredictionBootstrapResourceDto.failure(new PredictionBootstrapErrorDto(
                    resourceName + " 조회가 중단되었습니다.",
                    503,
                    "PREDICTION_BOOTSTRAP_INTERRUPTED"));
            recordResourceDuration(resourceName, "interrupted", elapsedNanos(waitStartedAtNanos));
            return resource;
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                PredictionBootstrapResourceDto<T> resource = PredictionBootstrapResourceDto.failure(
                        toErrorDto(runtimeException));
                recordResourceDuration(resourceName, "failure", elapsedNanos(waitStartedAtNanos));
                return resource;
            }
            PredictionBootstrapResourceDto<T> resource = PredictionBootstrapResourceDto.failure(new PredictionBootstrapErrorDto(
                    resourceName + " 조회에 실패했습니다.",
                    500,
                    "PREDICTION_BOOTSTRAP_RESOURCE_FAILED"));
            recordResourceDuration(resourceName, "failure", elapsedNanos(waitStartedAtNanos));
            return resource;
        }
    }

    private <T> PredictionBootstrapResourceDto<T> timeoutResource(
            Future<TimedResource<T>> future,
            String resourceName,
            long waitStartedAtNanos) {
        future.cancel(true);
        log.warn(
                "prediction.bootstrap.resource_timed_out resource={} timeoutMs={}",
                resourceName,
                resourceTimeout.toMillis());
        PredictionBootstrapResourceDto<T> resource = PredictionBootstrapResourceDto.failure(new PredictionBootstrapErrorDto(
                resourceName + " 조회 시간이 초과되었습니다.",
                504,
                "PREDICTION_BOOTSTRAP_RESOURCE_TIMEOUT"));
        recordResourceDuration(resourceName, "timeout", elapsedNanos(waitStartedAtNanos));
        return resource;
    }

    private <T> TimedResource<T> loadTimedResource(Supplier<PredictionBootstrapResourceDto<T>> loader) {
        long startedAtNanos = System.nanoTime();
        PredictionBootstrapResourceDto<T> resource = loader.get();
        return new TimedResource<>(resource, elapsedNanos(startedAtNanos));
    }

    private <T> TimedResource<T> loadTimedResourceWithPermit(
            String resourceName,
            Supplier<PredictionBootstrapResourceDto<T>> loader) {
        long startedAtNanos = System.nanoTime();
        boolean acquired = false;
        try {
            acquired = resourceBulkhead.tryAcquire(
                    resourcePermitWaitTimeout.toNanos(),
                    TimeUnit.NANOSECONDS);
            if (!acquired) {
                return new TimedResource<>(
                        PredictionBootstrapResourceDto.failure(new PredictionBootstrapErrorDto(
                                resourceName + " 조회가 지연되고 있습니다.",
                                503,
                                "PREDICTION_BOOTSTRAP_RESOURCE_BUSY")),
                        elapsedNanos(startedAtNanos));
            }
            return loadTimedResource(loader);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new TimedResource<>(
                    PredictionBootstrapResourceDto.failure(new PredictionBootstrapErrorDto(
                            resourceName + " 조회가 중단되었습니다.",
                            503,
                            "PREDICTION_BOOTSTRAP_INTERRUPTED")),
                    elapsedNanos(startedAtNanos));
        } finally {
            if (acquired) {
                resourceBulkhead.release();
            }
        }
    }

    private Duration normalizeResourceTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return DEFAULT_RESOURCE_TIMEOUT;
        }
        return timeout;
    }

    private Duration normalizePermitWaitTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return DEFAULT_RESOURCE_PERMIT_WAIT_TIMEOUT;
        }
        return timeout;
    }

    private void registerResourceBulkheadMetrics() {
        Gauge.builder(
                        "prediction_bootstrap_resource_active",
                        resourceBulkhead,
                        semaphore -> resourceMaxConcurrency - semaphore.availablePermits())
                .description("Active prediction bootstrap detail and vote-status loaders")
                .register(meterRegistry);
        Gauge.builder(
                        "prediction_bootstrap_resource_limit",
                        this,
                        ignored -> resourceMaxConcurrency)
                .description("Maximum concurrent prediction bootstrap resource loaders")
                .register(meterRegistry);
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

    private String bootstrapRequestKey(LocalDate date, String gameId) {
        return "predictionBootstrap:date:" + date + ":game:" + (gameId == null ? "" : gameId);
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

    private void recordBootstrapEvent(String operation, String result) {
        Counter.builder(BOOTSTRAP_EVENT_METRIC)
                .description("Prediction bootstrap request events")
                .tags(
                        "operation", normalizeMetricTag(operation),
                        "result", normalizeMetricTag(result))
                .register(meterRegistry)
                .increment();
    }

    private <T> String classifyResourceResult(PredictionBootstrapResourceDto<T> resource) {
        return resource != null && resource.ok() ? "success" : "failure";
    }

    private long elapsedNanos(long startedAtNanos) {
        return System.nanoTime() - startedAtNanos;
    }

    private void recordResourceDuration(String resourceName, String result, long durationNanos) {
        if (durationNanos < 0) {
            return;
        }

        Timer.builder(RESOURCE_DURATION_METRIC)
                .description("Prediction bootstrap resource duration")
                .publishPercentileHistogram()
                .tags(
                        "resource", normalizeMetricTag(resourceName),
                        "result", normalizeMetricTag(result))
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private String normalizeMetricTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-]", "_");
    }

    private record TimedResource<T>(PredictionBootstrapResourceDto<T> resource, long durationNanos) {
    }

    private record CachedManualDataFailure(ManualBaseballDataRequest request, long expiresAtNanos) {
    }
}
