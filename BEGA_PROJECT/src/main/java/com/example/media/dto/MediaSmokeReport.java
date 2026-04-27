package com.example.media.dto;

import java.util.List;

public record MediaSmokeReport(
        int sampleLimit,
        List<String> requestedDomains,
        List<MediaSmokeDomainReport> domains,
        boolean hasFailures) {
}
