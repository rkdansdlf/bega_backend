package com.example.common.clienterror.dto;

import java.time.OffsetDateTime;

public record ClientErrorEventSummaryDto(
        String eventId,
        String bucket,
        String source,
        String message,
        Integer statusCode,
        String statusGroup,
        String responseCode,
        String route,
        String normalizedRoute,
        String method,
        String endpoint,
        String normalizedEndpoint,
        String fingerprint,
        OffsetDateTime occurredAt,
        String sessionId,
        Long userId,
        Integer feedbackCount) {
}
