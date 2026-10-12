package com.example.notification.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record MarkAllAsReadRequest(@NotNull Instant readThrough) {
}
