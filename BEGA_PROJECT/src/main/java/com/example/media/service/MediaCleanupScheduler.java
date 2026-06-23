package com.example.media.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MediaCleanupScheduler {

    private final MediaUploadService mediaUploadService;
    private final boolean enabled;

    public MediaCleanupScheduler(
            MediaUploadService mediaUploadService,
            @Value("${media.cleanup.enabled:true}") boolean enabled) {
        this.mediaUploadService = mediaUploadService;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${media.cleanup.fixed-delay-ms:900000}")
    public void cleanupExpiredAssets() {
        if (!enabled) {
            log.debug("Skipping media cleanup scheduler because media.cleanup.enabled=false");
            return;
        }
        log.debug("Running media cleanup scheduler");
        mediaUploadService.cleanupExpiredPendingAssets();
        mediaUploadService.cleanupUnlinkedReadyAssets();
    }
}
