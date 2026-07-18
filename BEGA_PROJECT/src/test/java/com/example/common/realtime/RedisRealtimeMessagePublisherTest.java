package com.example.common.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

class RedisRealtimeMessagePublisherTest {

    private static final String CHANNEL = "bega:realtime:v1";

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final RealtimeMessageDispatcher fallbackDispatcher = mock(RealtimeMessageDispatcher.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RedisRealtimeMessagePublisher publisher = new RedisRealtimeMessagePublisher(
            redisTemplate,
            objectMapper,
            fallbackDispatcher,
            CHANNEL);

    @Test
    void broadcastPublishesVersionedEnvelopeToRedis() throws Exception {
        when(redisTemplate.convertAndSend(eq(CHANNEL), org.mockito.ArgumentMatchers.anyString())).thenReturn(2L);

        publisher.broadcast("/topic/party/5", Map.of("message", "hello"));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq(CHANNEL), payloadCaptor.capture());
        RealtimeMessageEnvelope envelope = objectMapper.readValue(
                payloadCaptor.getValue(),
                RealtimeMessageEnvelope.class);
        assertThat(envelope.version()).isEqualTo(RealtimeMessageEnvelope.CURRENT_VERSION);
        assertThat(envelope.eventId()).isNotBlank();
        assertThat(envelope.target()).isEqualTo(RealtimeMessageEnvelope.Target.BROADCAST);
        assertThat(envelope.destination()).isEqualTo("/topic/party/5");
        assertThat(envelope.userId()).isNull();
        assertThat(envelope.payload().get("message").asText()).isEqualTo("hello");
    }

    @Test
    void publishFailureFallsBackToCurrentInstance() {
        when(redisTemplate.convertAndSend(eq(CHANNEL), org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new IllegalStateException("redis unavailable"));

        publisher.sendToUser("42", "/queue/notifications", Map.of("id", 7));

        ArgumentCaptor<RealtimeMessageEnvelope> envelopeCaptor =
                ArgumentCaptor.forClass(RealtimeMessageEnvelope.class);
        verify(fallbackDispatcher).dispatch(envelopeCaptor.capture());
        assertThat(envelopeCaptor.getValue().target()).isEqualTo(RealtimeMessageEnvelope.Target.USER);
        assertThat(envelopeCaptor.getValue().userId()).isEqualTo("42");
    }

    @Test
    void zeroSubscribersFallsBackToCurrentInstance() {
        when(redisTemplate.convertAndSend(eq(CHANNEL), org.mockito.ArgumentMatchers.anyString())).thenReturn(0L);

        publisher.broadcast("/topic/party/5", Map.of("message", "hello"));

        verify(fallbackDispatcher).dispatch(org.mockito.ArgumentMatchers.any(RealtimeMessageEnvelope.class));
    }

    @Test
    void strictTransportPreservesStableEventId() throws Exception {
        when(redisTemplate.convertAndSend(eq(CHANNEL), org.mockito.ArgumentMatchers.anyString())).thenReturn(2L);
        RealtimeMessageEnvelope envelope = RealtimeMessageEnvelope.broadcast(
                "stable-event-id",
                "/topic/party/5",
                objectMapper.valueToTree(Map.of("message", "hello")));

        publisher.publish(envelope);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq(CHANNEL), payloadCaptor.capture());
        RealtimeMessageEnvelope published = objectMapper.readValue(
                payloadCaptor.getValue(),
                RealtimeMessageEnvelope.class);
        assertThat(published.eventId()).isEqualTo("stable-event-id");
        assertThat(published.version()).isEqualTo(RealtimeMessageEnvelope.CURRENT_VERSION);
        verifyNoInteractions(fallbackDispatcher);
    }

    @Test
    void strictTransportRejectsZeroSubscribersWithoutLocalFallback() {
        when(redisTemplate.convertAndSend(eq(CHANNEL), org.mockito.ArgumentMatchers.anyString())).thenReturn(0L);
        RealtimeMessageEnvelope envelope = RealtimeMessageEnvelope.broadcast(
                "stable-event-id",
                "/topic/party/5",
                objectMapper.valueToTree(Map.of("message", "hello")));

        assertThatThrownBy(() -> publisher.publish(envelope))
                .isInstanceOf(RealtimeMessageTransportException.class)
                .hasMessageContaining("no subscribers");

        verifyNoInteractions(fallbackDispatcher);
    }

    @Test
    void strictTransportPropagatesRedisFailureWithoutLocalFallback() {
        when(redisTemplate.convertAndSend(eq(CHANNEL), org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new IllegalStateException("redis unavailable"));
        RealtimeMessageEnvelope envelope = RealtimeMessageEnvelope.user(
                "stable-event-id",
                "42",
                "/queue/notifications",
                objectMapper.valueToTree(Map.of("id", 7)));

        assertThatThrownBy(() -> publisher.publish(envelope))
                .isInstanceOf(RealtimeMessageTransportException.class)
                .hasCauseInstanceOf(IllegalStateException.class);

        verifyNoInteractions(fallbackDispatcher);
    }

    @Test
    void fallbackFailureIsNotRetriedLocally() {
        when(redisTemplate.convertAndSend(eq(CHANNEL), org.mockito.ArgumentMatchers.anyString())).thenReturn(0L);
        doThrow(new IllegalStateException("local broker unavailable"))
                .when(fallbackDispatcher)
                .dispatch(org.mockito.ArgumentMatchers.any(RealtimeMessageEnvelope.class));

        assertThatThrownBy(() -> publisher.broadcast("/topic/party/5", Map.of("message", "hello")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("local broker unavailable");
        verify(fallbackDispatcher, times(1))
                .dispatch(org.mockito.ArgumentMatchers.any(RealtimeMessageEnvelope.class));
    }

    @Test
    void unregisteredDestinationIsRejectedBeforeRedisPublish() {
        assertThatThrownBy(() -> publisher.broadcast("/topic/unregistered", Map.of("message", "hello")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("destination");

        verifyNoInteractions(redisTemplate, fallbackDispatcher);
    }

    @Test
    void battleWildcardDestinationIsRejectedBeforeRedisPublish() {
        assertThatThrownBy(() -> publisher.broadcast("/topic/battle/*", Map.of("KIA", 3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("destination");

        verifyNoInteractions(redisTemplate, fallbackDispatcher);
    }
}
