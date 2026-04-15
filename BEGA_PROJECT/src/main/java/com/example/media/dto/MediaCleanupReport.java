package com.example.media.dto;

import java.util.List;

public record MediaCleanupReport(
        List<String> requestedTargets,
        List<MediaCleanupTargetReport> targets,
        boolean hasFailures) {
}
