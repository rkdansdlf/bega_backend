package com.example.common.clienterror.dto;

import java.time.OffsetDateTime;

public record ClientErrorTopFingerprintDto(
        String fingerprint,
        String bucket,
        String source,
        String message,
        String route,
        String endpoint,
        String statusGroup,
        String method,
        long count,
        long uniqueSessions,
        String latestEventId,
        OffsetDateTime latestOccurredAt,
        OffsetDateTime latestAlertSentAt,
        String latestAlertChannel) {
}
