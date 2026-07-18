package com.example.common.realtime;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "app.realtime", name = "transport", havingValue = "redis", matchIfMissing = true)
public class RedisRealtimeMessagePublisher implements RealtimeMessagePublisher, RealtimeMessageTransport {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RealtimeMessageDispatcher fallbackDispatcher;
    private final String redisChannel;

    public RedisRealtimeMessagePublisher(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RealtimeMessageDispatcher fallbackDispatcher,
            @Value("${app.realtime.redis-channel:bega:realtime:v1}") String redisChannel) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.fallbackDispatcher = fallbackDispatcher;
        this.redisChannel = redisChannel;
    }

    @Override
    public void broadcast(String destination, Object payload) {
        publishBestEffort(RealtimeMessageEnvelope.broadcast(
                UUID.randomUUID().toString(),
                destination,
                objectMapper.valueToTree(payload)));
    }

    @Override
    public void sendToUser(String userId, String destination, Object payload) {
        publishBestEffort(RealtimeMessageEnvelope.user(
                UUID.randomUUID().toString(),
                userId,
                destination,
                objectMapper.valueToTree(payload)));
    }

    @Override
    public void publish(RealtimeMessageEnvelope envelope) {
        RealtimeMessageEnvelopeValidator.validate(envelope);

        String serializedEnvelope;
        try {
            serializedEnvelope = objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new RealtimeMessageTransportException(
                    "Realtime Redis serialization failed",
                    e);
        }
        RealtimeMessageEnvelopeValidator.validateSerialized(serializedEnvelope);

        try {
            Long subscriberCount = redisTemplate.convertAndSend(redisChannel, serializedEnvelope);
            if (subscriberCount == null || subscriberCount <= 0) {
                throw new RealtimeMessageTransportException(
                        "Realtime Redis channel has no subscribers");
            }
        } catch (RealtimeMessageTransportException e) {
            throw e;
        } catch (Exception e) {
            throw new RealtimeMessageTransportException("Realtime Redis publish failed", e);
        }
    }

    private void publishBestEffort(RealtimeMessageEnvelope envelope) {
        RealtimeMessageEnvelopeValidator.validate(envelope);

        String serializedEnvelope;
        try {
            serializedEnvelope = objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            log.warn(
                    "Realtime Redis serialization failed; using local fallback eventId={} destination={}",
                    envelope.eventId(),
                    envelope.destination(),
                    e);
            fallbackDispatcher.dispatch(envelope);
            return;
        }

        RealtimeMessageEnvelopeValidator.validateSerialized(serializedEnvelope);

        try {
            Long subscriberCount = redisTemplate.convertAndSend(redisChannel, serializedEnvelope);
            if (subscriberCount != null && subscriberCount > 0) {
                return;
            }

            log.warn(
                    "Realtime Redis channel has no subscribers; using local fallback eventId={} destination={}",
                    envelope.eventId(),
                    envelope.destination());
        } catch (Exception e) {
            log.warn(
                    "Realtime Redis publish failed; using local fallback eventId={} destination={}",
                    envelope.eventId(),
                    envelope.destination(),
                    e);
        }

        fallbackDispatcher.dispatch(envelope);
    }
}
