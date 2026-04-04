package com.example.auth.dto;

public record AvailabilityCheckResponseDto(
        boolean available,
        String normalized) {
}
