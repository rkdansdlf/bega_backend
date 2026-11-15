package com.example.notification.dto;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public record MarkAllAsReadResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Long> notificationIds) {
    public MarkAllAsReadResponse {
        notificationIds = List.copyOf(notificationIds);
    }
}
