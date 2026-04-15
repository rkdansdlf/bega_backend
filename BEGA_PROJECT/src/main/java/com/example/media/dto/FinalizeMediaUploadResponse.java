package com.example.media.dto;

public record FinalizeMediaUploadResponse(
        Long assetId,
        String storagePath,
        String publicUrl) {
}
