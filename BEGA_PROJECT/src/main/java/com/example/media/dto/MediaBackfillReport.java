package com.example.media.dto;

import java.util.List;

public record MediaBackfillReport(
        boolean applied,
        int batchSize,
        List<String> requestedDomains,
        List<MediaBackfillDomainReport> domains,
        boolean hasFailures) {
}
