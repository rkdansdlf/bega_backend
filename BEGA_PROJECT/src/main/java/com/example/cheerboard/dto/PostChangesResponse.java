package com.example.cheerboard.dto;

/**
 * Lightweight response for polling endpoint to check for new posts
 * - Minimal payload for efficient polling
 * - Frontend can poll this endpoint frequently and only fetch full data when changes are detected
 */
public record PostChangesResponse(
        int newCount,     // Number of new posts since the given ID
        Long latestId     // ID of the most recent post (null if no posts exist)
) {
}
