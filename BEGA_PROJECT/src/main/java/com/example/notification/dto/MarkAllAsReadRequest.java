package com.example.notification.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record MarkAllAsReadRequest(
        @NotNull @Size(max = 100) List<@NotNull Long> notificationIds) {
}
