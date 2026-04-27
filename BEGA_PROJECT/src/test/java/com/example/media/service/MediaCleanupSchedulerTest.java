package com.example.media.service;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaCleanupSchedulerTest {

    @InjectMocks
    private MediaCleanupScheduler mediaCleanupScheduler;

    @Mock
    private MediaUploadService mediaUploadService;

    @Test
    @DisplayName("cleanup scheduler는 만료 pending과 orphan ready 정리를 모두 호출한다")
    void cleanupExpiredAssets_runsBothCleanupPaths() {
        mediaCleanupScheduler.cleanupExpiredAssets();

        verify(mediaUploadService).cleanupExpiredPendingAssets();
        verify(mediaUploadService).cleanupUnlinkedReadyAssets();
    }
}
