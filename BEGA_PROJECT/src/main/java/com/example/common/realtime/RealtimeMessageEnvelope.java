package com.example.common.realtime;

import com.fasterxml.jackson.databind.JsonNode;

public record RealtimeMessageEnvelope(
        int version,
        String eventId,
        Target target,
        String destination,
        String userId,
        JsonNode payload) {

    public static final int CURRENT_VERSION = 1;

    public enum Target {
        BROADCAST,
        USER
    }

    public static RealtimeMessageEnvelope broadcast(
            String eventId,
            String destination,
            JsonNode payload) {
        return new RealtimeMessageEnvelope(
                CURRENT_VERSION,
                eventId,
                Target.BROADCAST,
                destination,
                null,
                payload);
    }

    public static RealtimeMessageEnvelope user(
            String eventId,
            String userId,
            String destination,
            JsonNode payload) {
        return new RealtimeMessageEnvelope(
                CURRENT_VERSION,
                eventId,
                Target.USER,
                destination,
                userId,
                payload);
    }
}
