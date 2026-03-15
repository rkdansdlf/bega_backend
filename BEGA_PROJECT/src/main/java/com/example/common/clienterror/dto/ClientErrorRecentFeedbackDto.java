package com.example.common.clienterror.dto;

import java.time.OffsetDateTime;

public record ClientErrorRecentFeedbackDto(
        String eventId,
        String route,
        String actionTaken,
        String comment,
        OffsetDateTime occurredAt) {
}
