package com.example.common.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public record RealtimeOutboxClaim(
        Long id,
        int envelopeVersion,
        String eventId,
        RealtimeMessageEnvelope.Target target,
        String destination,
        String userId,
        String payload,
        int attemptCount) {

    static RealtimeOutboxClaim from(RealtimeOutboxEvent event) {
        return new RealtimeOutboxClaim(
                event.getId(),
                event.getEnvelopeVersion(),
                event.getEventId(),
                event.getTarget(),
                event.getDestination(),
                event.getUserId(),
                event.getPayload(),
                event.getAttemptCount());
    }

    RealtimeMessageEnvelope toEnvelope(ObjectMapper objectMapper) {
        try {
            JsonNode payloadNode = objectMapper.readTree(payload);
            return new RealtimeMessageEnvelope(
                    envelopeVersion,
                    eventId,
                    target,
                    destination,
                    userId,
                    payloadNode);
        } catch (Exception e) {
            throw new IllegalArgumentException("Realtime outbox payload is not valid JSON", e);
        }
    }
}
