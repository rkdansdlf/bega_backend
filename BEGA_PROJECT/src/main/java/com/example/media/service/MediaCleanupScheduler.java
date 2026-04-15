package com.example.media.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MediaCleanupScheduler {

    private final MediaUploadService mediaUploadService;

    @Scheduled(fixedDelayString = "${media.cleanup.fixed-delay-ms:900000}")
    public void cleanupExpiredAssets() {
        log.debug("Running media cleanup scheduler");
        mediaUploadService.cleanupExpiredPendingAssets();
        mediaUploadService.cleanupUnlinkedReadyAssets();
    }
}
