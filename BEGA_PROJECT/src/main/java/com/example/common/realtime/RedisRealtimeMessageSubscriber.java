package com.example.common.realtime;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.realtime", name = "transport", havingValue = "redis", matchIfMissing = true)
public class RedisRealtimeMessageSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final RealtimeMessageDispatcher dispatcher;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        handleMessage(new String(message.getBody(), StandardCharsets.UTF_8));
    }

    public void handleMessage(String serializedEnvelope) {
        try {
            RealtimeMessageEnvelopeValidator.validateSerialized(serializedEnvelope);
            RealtimeMessageEnvelope envelope = objectMapper.readValue(
                    serializedEnvelope,
                    RealtimeMessageEnvelope.class);
            RealtimeMessageEnvelopeValidator.validate(envelope);
            dispatcher.dispatch(envelope);
        } catch (Exception e) {
            log.warn("Realtime Redis message was rejected", e);
        }
    }
}
