package com.example.media.dto;

import java.util.List;
import java.util.Map;

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
        List<String> sampleManualReviewTargets,
        Map<String, Integer> auditCounts,
        List<MediaBackfillIssueSample> auditSamples) {

    public MediaBackfillDomainReport(
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
        this(
                domain,
                scannedCount,
                normalizedCount,
                updatedCount,
                clearedCount,
                linkSyncedCount,
                legacyPathRetainedCount,
                manualReviewCount,
                sampleNormalizedTargets,
                sampleLegacyRetainedTargets,
                sampleManualReviewTargets,
                Map.of(),
                List.of());
    }
}
