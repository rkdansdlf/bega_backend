package com.example.common.realtime;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@ConditionalOnProperty(
        prefix = "app.realtime.outbox",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RealtimeOutboxRelay {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final RealtimeOutboxStateService stateService;
    private final RealtimeMessageTransport transport;
    private final RealtimeOutboxProperties properties;
    private final ObjectMapper objectMapper;
    private final String workerId = UUID.randomUUID().toString();
    private final Counter publishSuccessCounter;
    private final Counter retryCounter;
    private final Counter deadCounter;
    private final Counter claimWonCounter;
    private final Counter claimLostCounter;
    private final Counter leaseLostCounter;
    private final Timer publishTimer;

    public RealtimeOutboxRelay(
            RealtimeOutboxStateService stateService,
            RealtimeMessageTransport transport,
            RealtimeOutboxProperties properties,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper) {
        this.stateService = stateService;
        this.transport = transport;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.publishSuccessCounter = Counter.builder("realtime.outbox.publish")
                .tag("outcome", "success")
                .register(meterRegistry);
        this.retryCounter = Counter.builder("realtime.outbox.publish")
                .tag("outcome", "retry")
                .register(meterRegistry);
        this.deadCounter = Counter.builder("realtime.outbox.publish")
                .tag("outcome", "dead")
                .register(meterRegistry);
        this.claimWonCounter = Counter.builder("realtime.outbox.claim")
                .tag("outcome", "won")
                .register(meterRegistry);
        this.claimLostCounter = Counter.builder("realtime.outbox.claim")
                .tag("outcome", "lost")
                .register(meterRegistry);
        this.leaseLostCounter = Counter.builder("realtime.outbox.lease.lost")
                .register(meterRegistry);
        this.publishTimer = Timer.builder("realtime.outbox.publish.duration")
                .register(meterRegistry);
        Gauge.builder("realtime.outbox.pending", stateService, RealtimeOutboxStateService::countActive)
                .register(meterRegistry);
        Gauge.builder("realtime.outbox.oldest.age", stateService, this::oldestAgeSeconds)
                .register(meterRegistry);
    }

    @Scheduled(
            fixedDelayString = "${app.realtime.outbox.poll-interval-ms:200}",
            scheduler = "realtimeOutboxTaskScheduler")
    void poll() {
        Instant scanTime = Instant.now();
        List<Long> ids = stateService.findClaimableIds(scanTime, properties.getBatchSize());
        for (Long id : ids) {
            relayOne(id);
        }
    }

    @Scheduled(
            cron = "${app.realtime.outbox.cleanup-cron:0 15 * * * *}",
            scheduler = "realtimeOutboxTaskScheduler")
    void cleanup() {
        Instant cutoff = Instant.now().minus(properties.getRetention());
        int batchSize = Math.max(1, properties.getCleanupBatchSize());
        int maxBatches = Math.max(1, properties.getCleanupMaxBatches());
        int totalDeleted = 0;
        for (int batch = 0; batch < maxBatches; batch++) {
            int deleted = stateService.cleanupPublishedBefore(cutoff, batchSize);
            totalDeleted += deleted;
            if (deleted < batchSize) {
                break;
            }
        }
        if (totalDeleted > 0) {
            log.info("Cleaned up published realtime outbox events count={}", totalDeleted);
        }
    }

    private void relayOne(Long id) {
        Instant claimTime = Instant.now();
        Optional<RealtimeOutboxClaim> claim = stateService.claim(
                id,
                workerId,
                claimTime,
                properties.getLeaseDuration());
        if (claim.isEmpty()) {
            claimLostCounter.increment();
            return;
        }
        claimWonCounter.increment();
        publishClaim(claim.get());
    }

    private void publishClaim(RealtimeOutboxClaim claim) {
        Timer.Sample sample = Timer.start();
        try {
            transport.publish(claim.toEnvelope(objectMapper));
            if (!stateService.markPublished(claim.id(), workerId, Instant.now())) {
                leaseLostCounter.increment();
                log.warn(
                        "Realtime outbox publish lease was lost outboxId={} eventId={} target={} destination={} attempt={} workerId={}",
                        claim.id(),
                        claim.eventId(),
                        claim.target(),
                        claim.destination(),
                        claim.attemptCount(),
                        workerId);
                return;
            }
            publishSuccessCounter.increment();
        } catch (Exception e) {
            handleFailure(claim, e);
        } finally {
            sample.stop(publishTimer);
        }
    }

    private void handleFailure(RealtimeOutboxClaim claim, Exception failure) {
        boolean dead = claim.attemptCount() >= properties.getMaxAttempts();
        RealtimeOutboxStatus nextStatus = dead
                ? RealtimeOutboxStatus.DEAD
                : RealtimeOutboxStatus.RETRY;
        Instant availableAt = dead
                ? Instant.now()
                : Instant.now().plus(retryDelay(claim.attemptCount()));
        String error = sanitizeError(failure);
        boolean transitioned = stateService.markFailure(
                claim.id(),
                workerId,
                nextStatus,
                availableAt,
                error);
        if (!transitioned) {
            leaseLostCounter.increment();
            log.warn(
                    "Realtime outbox failure lease was lost outboxId={} eventId={} target={} destination={} attempt={} workerId={}",
                    claim.id(),
                    claim.eventId(),
                    claim.target(),
                    claim.destination(),
                    claim.attemptCount(),
                    workerId);
            return;
        }
        if (dead) {
            deadCounter.increment();
            log.error(
                    "Realtime outbox event exhausted retries outboxId={} eventId={} target={} destination={} attempt={} workerId={} error={}",
                    claim.id(),
                    claim.eventId(),
                    claim.target(),
                    claim.destination(),
                    claim.attemptCount(),
                    workerId,
                    error);
        } else {
            retryCounter.increment();
            log.warn(
                    "Realtime outbox publish scheduled for retry outboxId={} eventId={} target={} destination={} attempt={} workerId={} error={}",
                    claim.id(),
                    claim.eventId(),
                    claim.target(),
                    claim.destination(),
                    claim.attemptCount(),
                    workerId,
                    error);
        }
    }

    private Duration retryDelay(int attemptCount) {
        long baseMillis = Math.max(1L, properties.getRetryBaseDelay().toMillis());
        long maxMillis = Math.max(baseMillis, properties.getRetryMaxDelay().toMillis());
        int exponent = Math.max(0, Math.min(30, attemptCount - 1));
        long exponential;
        try {
            exponential = Math.multiplyExact(baseMillis, 1L << exponent);
        } catch (ArithmeticException e) {
            exponential = maxMillis;
        }
        long capped = Math.min(maxMillis, exponential);
        long jitterBound = Math.max(1L, capped / 4L);
        long jitter = ThreadLocalRandom.current().nextLong(jitterBound);
        return Duration.ofMillis(Math.min(maxMillis, capped + jitter));
    }

    private String sanitizeError(Exception failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure.getClass().getSimpleName();
        }
        String sanitized = message.replace('\n', ' ').replace('\r', ' ');
        return sanitized.length() <= MAX_ERROR_LENGTH
                ? sanitized
                : sanitized.substring(0, MAX_ERROR_LENGTH);
    }

    private double oldestAgeSeconds(RealtimeOutboxStateService service) {
        Instant oldest = service.findOldestActiveCreatedAt();
        if (oldest == null) {
            return 0.0;
        }
        return Math.max(0L, Duration.between(oldest, Instant.now()).toSeconds());
    }
}
