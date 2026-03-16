package com.example.common.clienterror.dto;

import java.time.OffsetDateTime;

public record ClientErrorAlertNotificationDto(
        Long id,
        String fingerprint,
        String bucket,
        String source,
        String channel,
        String route,
        String statusGroup,
        long observedCount,
        int thresholdCount,
        int windowMinutes,
        String latestEventId,
        String latestMessage,
        OffsetDateTime latestOccurredAt,
        OffsetDateTime notifiedAt,
        String deliveryStatus,
        String failureReason) {
}
