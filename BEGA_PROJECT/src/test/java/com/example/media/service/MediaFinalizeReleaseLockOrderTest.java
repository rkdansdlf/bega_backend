package com.example.media.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.example.media.entity.MediaAsset;
import com.example.media.repository.MediaAssetRepository;
import com.example.media.repository.MediaFinalizeAttemptRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class MediaFinalizeReleaseLockOrderTest {
    @Test
    void releaseLocksAssetBeforeAttemptLikeAccountCleanup() {
        MediaAssetRepository assets = mock(MediaAssetRepository.class);
        MediaFinalizeAttemptRepository attempts = mock(MediaFinalizeAttemptRepository.class);
        MediaFinalizeClaimService service = new MediaFinalizeClaimService(assets, attempts,
                mock(com.example.BegaDiary.Repository.SeatViewPhotoRepository.class),
                mock(com.example.cheerboard.storage.config.StorageConfig.class),
                mock(jakarta.persistence.EntityManager.class));
        MediaAsset asset = MediaAsset.builder().id(17L).build();

        service.release(new MediaFinalizeClaimService.FinalizeClaim(asset, "synthetic-token"));

        InOrder order = inOrder(assets, attempts);
        order.verify(assets).releaseFinalizeClaim(eq(17L), eq("synthetic-token"), any());
        order.verify(attempts).markAttemptCleanupRequired(eq("synthetic-token"), any());
    }
}
