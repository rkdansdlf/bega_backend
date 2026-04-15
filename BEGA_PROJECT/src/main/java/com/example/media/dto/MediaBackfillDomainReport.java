package com.example.media.dto;

import java.util.List;

public record MediaBackfillDomainReport(
        String domain,
        int scannedCount,
        int normalizedCount,
        int updatedCount,
        int clearedCount,
        int linkSyncedCount,
        int legacyPathRetainedCount,
        int manualReviewCount,
        List<String> sampleNormalizedTargets,
        List<String> sampleLegacyRetainedTargets,
        List<String> sampleManualReviewTargets) {
}
