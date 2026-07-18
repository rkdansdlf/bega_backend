package com.example.common.realtime;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RealtimeOutboxWriter {

    private final RealtimeOutboxRepository repository;
    private final ObjectMapper objectMapper;

    public void broadcast(String destination, Object payload) {
        write(RealtimeMessageEnvelope.broadcast(
                UUID.randomUUID().toString(),
                destination,
                objectMapper.valueToTree(payload)));
    }

    public void sendToUser(String userId, String destination, Object payload) {
        write(RealtimeMessageEnvelope.user(
                UUID.randomUUID().toString(),
                userId,
                destination,
                objectMapper.valueToTree(payload)));
    }

    private void write(RealtimeMessageEnvelope envelope) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Realtime outbox write requires an active transaction");
        }

        RealtimeMessageEnvelopeValidator.validate(envelope);
        try {
            String serializedEnvelope = objectMapper.writeValueAsString(envelope);
            RealtimeMessageEnvelopeValidator.validateSerialized(serializedEnvelope);
            JsonNode payload = envelope.payload();
            repository.save(RealtimeOutboxEvent.pending(
                    envelope,
                    objectMapper.writeValueAsString(payload),
                    Instant.now()));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize realtime outbox event", e);
        }
    }
}
