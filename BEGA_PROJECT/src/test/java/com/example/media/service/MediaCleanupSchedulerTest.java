package com.example.media.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaCleanupSchedulerTest {

    @Mock
    private MediaUploadService mediaUploadService;

    @Test
    @DisplayName("cleanup scheduler는 finalize 시도 ledger, 만료 업로드, staging, tombstone, orphan을 정리한다")
    void cleanupExpiredAssets_runsBothCleanupPaths() {
        MediaCleanupScheduler mediaCleanupScheduler = new MediaCleanupScheduler(mediaUploadService, true);

        mediaCleanupScheduler.cleanupExpiredAssets();

        verify(mediaUploadService).cleanupExpiredPendingAssets();
        verify(mediaUploadService).cleanupExpiredFinalizeAttempts();
        verify(mediaUploadService).cleanupReadyStagingObjects();
        verify(mediaUploadService).cleanupDeletedFinalizeTombstones();
        verify(mediaUploadService).cleanupUnlinkedReadyAssets();
    }

    @Test
    @DisplayName("cleanup scheduler가 비활성화되면 DB cleanup을 실행하지 않는다")
    void cleanupExpiredAssets_skipsCleanupWhenDisabled() {
        MediaCleanupScheduler mediaCleanupScheduler = new MediaCleanupScheduler(mediaUploadService, false);

        mediaCleanupScheduler.cleanupExpiredAssets();

        verifyNoInteractions(mediaUploadService);
    }
}
