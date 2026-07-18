package com.example.common.realtime;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

final class RealtimeMessageEnvelopeValidator {

    static final int MAX_ENVELOPE_BYTES = 256 * 1024;

    private static final int MAX_EVENT_ID_LENGTH = 128;
    private static final int MAX_DESTINATION_LENGTH = 255;
    private static final int MAX_USER_ID_LENGTH = 128;
    private static final Pattern PARTY_TOPIC_PATTERN = Pattern.compile("^/topic/party/\\d+$");
    private static final Pattern DM_TOPIC_PATTERN = Pattern.compile("^/topic/dm/\\d+$");
    private static final Pattern BATTLE_TOPIC_PATTERN = Pattern.compile("^/topic/battle/[A-Za-z0-9_-]{1,64}$");
    private static final String NOTIFICATION_QUEUE = "/queue/notifications";

    private RealtimeMessageEnvelopeValidator() {
    }

    static void validate(RealtimeMessageEnvelope envelope) {
        if (envelope == null) {
            throw new IllegalArgumentException("Realtime envelope is required");
        }
        if (envelope.version() != RealtimeMessageEnvelope.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported realtime envelope version: " + envelope.version());
        }
        requireText(envelope.eventId(), MAX_EVENT_ID_LENGTH, "eventId");
        requireText(envelope.destination(), MAX_DESTINATION_LENGTH, "destination");
        if (envelope.target() == null) {
            throw new IllegalArgumentException("Realtime target is required");
        }
        if (envelope.payload() == null) {
            throw new IllegalArgumentException("Realtime payload is required");
        }
        if (envelope.payload().toString().getBytes(StandardCharsets.UTF_8).length > MAX_ENVELOPE_BYTES) {
            throw new IllegalArgumentException("Realtime payload exceeds the maximum size");
        }

        switch (envelope.target()) {
            case BROADCAST -> validateBroadcast(envelope);
            case USER -> validateUser(envelope);
        }
    }

    static void validateSerialized(String serializedEnvelope) {
        if (serializedEnvelope == null
                || serializedEnvelope.getBytes(StandardCharsets.UTF_8).length > MAX_ENVELOPE_BYTES) {
            throw new IllegalArgumentException("Realtime envelope exceeds the maximum size");
        }
    }

    private static void validateBroadcast(RealtimeMessageEnvelope envelope) {
        if (envelope.userId() != null && !envelope.userId().isBlank()) {
            throw new IllegalArgumentException("Broadcast realtime envelope must not define userId");
        }
        String destination = envelope.destination();
        if (!PARTY_TOPIC_PATTERN.matcher(destination).matches()
                && !DM_TOPIC_PATTERN.matcher(destination).matches()
                && !BATTLE_TOPIC_PATTERN.matcher(destination).matches()) {
            throw new IllegalArgumentException("Realtime broadcast destination is not allowed");
        }
    }

    private static void validateUser(RealtimeMessageEnvelope envelope) {
        requireText(envelope.userId(), MAX_USER_ID_LENGTH, "userId");
        if (!NOTIFICATION_QUEUE.equals(envelope.destination())) {
            throw new IllegalArgumentException("Realtime user destination is not allowed");
        }
    }

    private static void requireText(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Realtime " + fieldName + " is required");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException("Realtime " + fieldName + " exceeds the maximum length");
        }
    }
}
