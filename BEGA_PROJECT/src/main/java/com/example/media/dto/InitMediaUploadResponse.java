package com.example.media.dto;

import java.time.Instant;
import java.util.Map;

public record InitMediaUploadResponse(
        Long assetId,
        String uploadUrl,
        String stagingObjectKey,
        Instant expiresAt,
        Map<String, String> requiredHeaders) {
}
