package com.example.common.realtime;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.realtime", name = "transport", havingValue = "local")
public class LocalRealtimeMessagePublisher implements RealtimeMessagePublisher, RealtimeMessageTransport {

    private final ObjectMapper objectMapper;
    private final RealtimeMessageDispatcher dispatcher;

    @Override
    public void broadcast(String destination, Object payload) {
        dispatcher.dispatch(RealtimeMessageEnvelope.broadcast(
                UUID.randomUUID().toString(),
                destination,
                objectMapper.valueToTree(payload)));
    }

    @Override
    public void sendToUser(String userId, String destination, Object payload) {
        dispatcher.dispatch(RealtimeMessageEnvelope.user(
                UUID.randomUUID().toString(),
                userId,
                destination,
                objectMapper.valueToTree(payload)));
    }

    @Override
    public void publish(RealtimeMessageEnvelope envelope) {
        dispatcher.dispatch(envelope);
    }
}
