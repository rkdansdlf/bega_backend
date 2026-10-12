package com.example.media.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record FinalizeMediaUploadResponse(
        Long assetId,
        String storagePath,
        @Schema(nullable = true)
        String publicUrl) {
}
