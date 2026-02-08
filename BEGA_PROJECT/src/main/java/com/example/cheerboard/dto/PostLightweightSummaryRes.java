package com.example.cheerboard.dto;

import java.time.Instant;

/**
 * Lightweight response DTO for post list endpoints (optimized for polling and list views)
 * - Excludes full content (only includes truncated preview)
 * - Minimal data to reduce payload size
 * - Used for efficient polling and initial list loading
 */
public record PostLightweightSummaryRes(
        Long id,
        String contentPreview, // First 100 characters
        String imageUrl, // First image only (null if no images)
        int likeCount,
        int commentCount,
        Instant createdAt,
        // Author info (minimal)
        Long authorId,
        String authorNickname,
        String authorProfileImage
) {
    /**
     * Factory method to create lightweight summary with content truncation
     */
    public static PostLightweightSummaryRes of(
            Long id,
            String fullContent,
            String firstImageUrl,
            int likeCount,
            int commentCount,
            Instant createdAt,
            Long authorId,
            String authorNickname,
            String authorProfileImage) {

        // Truncate content to 100 characters
        String preview = fullContent != null && fullContent.length() > 100
                ? fullContent.substring(0, 100) + "..."
                : fullContent;

        return new PostLightweightSummaryRes(
                id,
                preview,
                firstImageUrl,
                likeCount,
                commentCount,
                createdAt,
                authorId,
                authorNickname,
                authorProfileImage
        );
    }
}
