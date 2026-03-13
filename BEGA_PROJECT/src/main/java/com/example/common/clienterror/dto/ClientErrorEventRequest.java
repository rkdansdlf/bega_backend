package com.example.common.clienterror.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientErrorEventRequest(
        @NotBlank @Size(max = 64) String eventId,
        @NotBlank @Size(max = 64) String category,
        @NotBlank @Size(max = 1000) String message,
        Integer statusCode,
        @Size(max = 64) String responseCode,
        @Size(max = 8000) String stack,
        @Size(max = 8000) String componentStack,
        @NotBlank @Size(max = 500) String route,
        @Size(max = 16) String method,
        @Size(max = 500) String endpoint,
        @NotBlank @Size(max = 64) String timestamp,
        @Size(max = 128) String sessionId,
        Long userId) {
}
