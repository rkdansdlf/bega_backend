package com.example.common.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.annotation.Scheduled;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class RealtimeOutboxRelayTest {

    private final RealtimeOutboxStateService stateService = mock(RealtimeOutboxStateService.class);
    private final RealtimeMessageTransport transport = mock(RealtimeMessageTransport.class);
    private final RealtimeOutboxProperties properties = new RealtimeOutboxProperties();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final RealtimeOutboxRelay relay = new RealtimeOutboxRelay(
            stateService,
            transport,
            properties,
            meterRegistry,
            new ObjectMapper());

    @Test
    void successfulDeliveryPreservesEventIdAndMarksPublished() {
        RealtimeOutboxClaim claim = new RealtimeOutboxClaim(
                1L,
                RealtimeMessageEnvelope.CURRENT_VERSION,
                "stable-event-id",
                RealtimeMessageEnvelope.Target.BROADCAST,
                "/topic/party/5",
                null,
                "{\"message\":\"hello\"}",
                1);
        when(stateService.findClaimableIds(any(Instant.class), eq(properties.getBatchSize())))
                .thenReturn(List.of(1L));
        when(stateService.claim(eq(1L), anyString(), any(Instant.class), eq(properties.getLeaseDuration())))
                .thenReturn(Optional.of(claim));
        when(stateService.markPublished(eq(1L), anyString(), any(Instant.class))).thenReturn(true);

        relay.poll();

        ArgumentCaptor<RealtimeMessageEnvelope> envelopeCaptor =
                ArgumentCaptor.forClass(RealtimeMessageEnvelope.class);
        verify(transport).publish(envelopeCaptor.capture());
        assertThat(envelopeCaptor.getValue().eventId()).isEqualTo("stable-event-id");
        verify(stateService).markPublished(eq(1L), anyString(), any(Instant.class));
        assertThat(meterRegistry.counter("realtime.outbox.publish", "outcome", "success").count())
                .isEqualTo(1.0);
    }

    @Test
    void failedDeliverySchedulesRetryWithoutMarkingPublished() {
        RealtimeOutboxClaim claim = new RealtimeOutboxClaim(
                2L,
                RealtimeMessageEnvelope.CURRENT_VERSION,
                "retry-event-id",
                RealtimeMessageEnvelope.Target.USER,
                "/queue/notifications",
                "42",
                "{\"id\":7}",
                1);
        when(stateService.findClaimableIds(any(Instant.class), eq(properties.getBatchSize())))
                .thenReturn(List.of(2L));
        when(stateService.claim(eq(2L), anyString(), any(Instant.class), eq(properties.getLeaseDuration())))
                .thenReturn(Optional.of(claim));
        org.mockito.Mockito.doThrow(new RealtimeMessageTransportException("redis unavailable"))
                .when(transport)
                .publish(any(RealtimeMessageEnvelope.class));
        when(stateService.markFailure(
                eq(2L),
                anyString(),
                eq(RealtimeOutboxStatus.RETRY),
                any(Instant.class),
                eq("redis unavailable"))).thenReturn(true);

        relay.poll();

        verify(stateService).markFailure(
                eq(2L),
                anyString(),
                eq(RealtimeOutboxStatus.RETRY),
                any(Instant.class),
                eq("redis unavailable"));
        org.mockito.Mockito.verify(stateService, org.mockito.Mockito.never())
                .markPublished(any(), anyString(), any(Instant.class));
    }

    @Test
    void finalFailedAttemptMovesEventToDeadLetterState() {
        RealtimeOutboxClaim claim = new RealtimeOutboxClaim(
                3L,
                RealtimeMessageEnvelope.CURRENT_VERSION,
                "dead-event-id",
                RealtimeMessageEnvelope.Target.BROADCAST,
                "/topic/party/5",
                null,
                "{}",
                properties.getMaxAttempts());
        when(stateService.findClaimableIds(any(Instant.class), eq(properties.getBatchSize())))
                .thenReturn(List.of(3L));
        when(stateService.claim(eq(3L), anyString(), any(Instant.class), eq(properties.getLeaseDuration())))
                .thenReturn(Optional.of(claim));
        org.mockito.Mockito.doThrow(new RealtimeMessageTransportException("redis unavailable"))
                .when(transport)
                .publish(any(RealtimeMessageEnvelope.class));
        when(stateService.markFailure(
                eq(3L),
                anyString(),
                eq(RealtimeOutboxStatus.DEAD),
                any(Instant.class),
                eq("redis unavailable"))).thenReturn(true);

        relay.poll();

        verify(stateService).markFailure(
                eq(3L),
                anyString(),
                eq(RealtimeOutboxStatus.DEAD),
                any(Instant.class),
                eq("redis unavailable"));
    }

    @Test
    void lostPublishLeaseIsMeasuredWithoutCountingSuccess() {
        RealtimeOutboxClaim claim = new RealtimeOutboxClaim(
                4L,
                RealtimeMessageEnvelope.CURRENT_VERSION,
                "lease-lost-event",
                RealtimeMessageEnvelope.Target.BROADCAST,
                "/topic/party/5",
                null,
                "{}",
                1);
        when(stateService.findClaimableIds(any(Instant.class), eq(properties.getBatchSize())))
                .thenReturn(List.of(4L));
        when(stateService.claim(eq(4L), anyString(), any(Instant.class), eq(properties.getLeaseDuration())))
                .thenReturn(Optional.of(claim));
        when(stateService.markPublished(eq(4L), anyString(), any(Instant.class))).thenReturn(false);

        relay.poll();

        assertThat(meterRegistry.counter("realtime.outbox.lease.lost").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("realtime.outbox.publish", "outcome", "success").count())
                .isZero();
    }

    @Test
    void cleanupDrainsMultipleBatchesUntilTheLastPartialBatch() {
        when(stateService.cleanupPublishedBefore(any(Instant.class), eq(properties.getCleanupBatchSize())))
                .thenReturn(properties.getCleanupBatchSize(), properties.getCleanupBatchSize(), 20);

        relay.cleanup();

        verify(stateService, org.mockito.Mockito.times(3))
                .cleanupPublishedBefore(any(Instant.class), eq(properties.getCleanupBatchSize()));
    }

    @Test
    void cleanupStopsAtConfiguredMaximumBatchCount() {
        when(stateService.cleanupPublishedBefore(any(Instant.class), eq(properties.getCleanupBatchSize())))
                .thenReturn(properties.getCleanupBatchSize());

        relay.cleanup();

        verify(stateService, org.mockito.Mockito.times(properties.getCleanupMaxBatches()))
                .cleanupPublishedBefore(any(Instant.class), eq(properties.getCleanupBatchSize()));
    }

    @Test
    void lostFailureLeaseIsNotCountedAsRetry() {
        RealtimeOutboxClaim claim = new RealtimeOutboxClaim(
                5L,
                RealtimeMessageEnvelope.CURRENT_VERSION,
                "failure-lease-lost-event",
                RealtimeMessageEnvelope.Target.USER,
                "/queue/notifications",
                "42",
                "{}",
                1);
        when(stateService.findClaimableIds(any(Instant.class), eq(properties.getBatchSize())))
                .thenReturn(List.of(5L));
        when(stateService.claim(eq(5L), anyString(), any(Instant.class), eq(properties.getLeaseDuration())))
                .thenReturn(Optional.of(claim));
        org.mockito.Mockito.doThrow(new RealtimeMessageTransportException("redis unavailable"))
                .when(transport)
                .publish(any(RealtimeMessageEnvelope.class));
        when(stateService.markFailure(
                eq(5L),
                anyString(),
                eq(RealtimeOutboxStatus.RETRY),
                any(Instant.class),
                eq("redis unavailable"))).thenReturn(false);

        relay.poll();

        assertThat(meterRegistry.counter("realtime.outbox.lease.lost").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("realtime.outbox.publish", "outcome", "retry").count())
                .isZero();
    }

    @Test
    void pollUsesDedicatedOutboxScheduler() throws Exception {
        Scheduled scheduled = RealtimeOutboxRelay.class.getDeclaredMethod("poll").getAnnotation(Scheduled.class);

        assertThat(scheduled.scheduler()).isEqualTo("realtimeOutboxTaskScheduler");
    }
}
