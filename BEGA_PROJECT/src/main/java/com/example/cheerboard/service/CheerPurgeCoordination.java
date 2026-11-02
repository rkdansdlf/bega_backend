package com.example.cheerboard.service;

import com.example.cheerboard.repo.CheerPostRepo;
import com.example.common.exception.ConflictBusinessException;
import com.example.media.repository.MediaAssetRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.data.domain.PageRequest;

/** The caller must hold the author's user-row write lock. */
public final class CheerPurgeCoordination {

    /**
     * A purge held only by a reported post never finishes, so it stops blocking the author after this long.
     * Purges without reports always block until they finish.
     */
    public static final Duration CHEER_PURGE_HOLD_LIMIT = Duration.ofDays(30);

    private CheerPurgeCoordination() {}

    public static void assertNoStartedPurge(CheerPostRepo posts, Long authorId) {
        if (!posts.findStartedPurgeIdsByAuthor(authorId, staleBefore(), PageRequest.of(0, 1)).isEmpty()) {
            throw new ConflictBusinessException("POST_PURGE_IN_PROGRESS", "게시글 영구 삭제가 진행 중입니다. 잠시 후 다시 시도해주세요.");
        }
    }

    public static void assertNoOwnedReservedAssets(MediaAssetRepository assets, Long ownerId) {
        if (assets.existsOwnerPurgePendingNotStale(ownerId, staleBefore())) {
            throw new ConflictBusinessException("POST_PURGE_IN_PROGRESS", "게시글 영구 삭제가 진행 중입니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private static LocalDateTime staleBefore() {
        return LocalDateTime.now(ZoneOffset.UTC).minus(CHEER_PURGE_HOLD_LIMIT);
    }
}
