package com.example.cheerboard.service;

import com.example.cheerboard.repo.CheerPostRepo;
import com.example.common.exception.ConflictBusinessException;
import com.example.media.entity.MediaAssetStatus;
import com.example.media.repository.MediaAssetRepository;
import org.springframework.data.domain.PageRequest;

/** The caller must hold the author's user-row write lock. */
public final class CheerPurgeCoordination {
    private CheerPurgeCoordination() {}

    public static void assertNoStartedPurge(CheerPostRepo posts, Long authorId) {
        if (!posts.findStartedPurgeIdsByAuthor(authorId, PageRequest.of(0, 1)).isEmpty()) {
            throw new ConflictBusinessException("POST_PURGE_IN_PROGRESS", "게시글 영구 삭제가 진행 중입니다. 잠시 후 다시 시도해주세요.");
        }
    }

    public static void assertNoOwnedReservedAssets(MediaAssetRepository assets, Long ownerId) {
        if (assets.existsByOwnerUserIdAndStatus(ownerId, MediaAssetStatus.CHEER_PURGE_PENDING)) {
            throw new ConflictBusinessException("POST_PURGE_IN_PROGRESS", "게시글 영구 삭제가 진행 중입니다. 잠시 후 다시 시도해주세요.");
        }
    }
}
