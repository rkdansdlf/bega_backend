package com.example.cheerboard.storage.dto;

import java.time.Instant;

/**
 * 서명된 URL 응답 DTO
 */
public record SignedUrlDto(
    String url,
    Instant expiresAt
) {}
