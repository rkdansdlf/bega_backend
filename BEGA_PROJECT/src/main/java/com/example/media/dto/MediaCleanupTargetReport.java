package com.example.media.dto;

import com.example.media.entity.MediaCleanupTarget;

public record MediaCleanupTargetReport(
        MediaCleanupTarget target,
        int scannedCount,
        int deletedCount,
        int errorCount) {
}
