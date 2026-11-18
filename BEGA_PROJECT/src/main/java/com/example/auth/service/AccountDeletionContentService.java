package com.example.auth.service;

import com.example.auth.entity.LegacyPostImageHold;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.LegacyPostImageHoldRepository;
import com.example.cheerboard.domain.CheerComment;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.repo.CheerCommentRepo;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.cheerboard.retention.ReportEvidenceMediaRetention;
import com.example.cheerboard.retention.ReportEvidenceMediaRetentionRepository;
import com.example.cheerboard.retention.ReportEvidenceMediaStatus;
import com.example.cheerboard.retention.ReportEvidenceRetentionPolicy;
import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.entity.PostImage;
import com.example.cheerboard.storage.service.ImageService;
import com.example.media.entity.MediaDomain;
import com.example.media.service.AccountCheerAssetVerifier;
import com.example.media.service.MediaUploadService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Redacts all authored public content and owned media only after every related moderation case is terminal. */
@Service
@RequiredArgsConstructor
public class AccountDeletionContentService {

    public enum Result { COMPLETED, MODERATION_PENDING, PURGE_PENDING, ASSET_CLEANUP_PENDING }

    private static final String REDACTED_POST = "게시글이 비식별 처리되었습니다.";
    private static final String REDACTED_COMMENT = "댓글이 비식별 처리되었습니다.";

    private final CheerPostRepo cheerPostRepo;
    private final CheerCommentRepo cheerCommentRepo;
    private final CheerReportRepo cheerReportRepo;
    private final MediaUploadService mediaUploadService;
    private final ImageService imageService;
    private final LegacyPostImageHoldRepository legacyPostImageHolds;
    private final ReportEvidenceMediaRetentionRepository evidenceRetention;
    private final StorageConfig storageConfig;
    private final AccountCheerAssetVerifier cheerAssetVerifier;

    @Transactional
    public Result anonymize(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("A persisted account ID is required for finalization.");
        }
        var authoredPosts = cheerPostRepo.findAllByAuthorIdIncludingDeletedForUpdate(userId);
        // Moderation is checked before purge. An open case also blocks the purge claim query, so waiting on a
        // started purge while a case is open would hold the identity until the case closes.
        Set<Long> queryProtectedPostIds = cheerReportRepo.findOpenModerationPostIdsByAuthorId(userId);
        if (queryProtectedPostIds == null || !queryProtectedPostIds.isEmpty()) {
            return Result.MODERATION_PENDING;
        }
        // A reported post is never hard deleted, so a purge that started on it will not finish and must not hold the account.
        if (authoredPosts.stream().anyMatch(post -> post.getPurgeStartedAt() != null
                        && !cheerPostRepo.existsReportByPostId(post.getId()))
                || mediaUploadService.hasPendingCheerPurge(userId)) {
            return Result.PURGE_PENDING;
        }
        // Image rows are cleared below, so every managed image must be owned and linked before that happens.
        if (!cheerAssetVerifier.allManagedImagesHaveOwnedLinks(userId, authoredPosts)) {
            return Result.PURGE_PENDING;
        }

        Set<Long> reportedPostIds = new HashSet<>();
        Set<String> registeredPaths = new HashSet<>();
        for (CheerPost post : authoredPosts) {
            // A reported post keeps its media linked and protected: its evidence is retained, so its storage
            // objects are not deleted. Its image rows are still cleared so the public image endpoint stops serving them.
            boolean reported = cheerPostRepo.existsReportByPostId(post.getId());
            if (reported) {
                reportedPostIds.add(post.getId());
            } else {
                mediaUploadService.unlinkEntityMedia(MediaDomain.CHEER, post.getId());
            }
            post.setContent(REDACTED_POST);
            post.setSourceUrl(null);
            post.setSourceTitle(null);
            post.setSourceAuthor(null);
            post.setSourceLicense(null);
            post.setSourceLicenseUrl(null);
            post.setSourceChangedNote(null);
            post.setSourceSnapshotType(null);
            if (post.getImages() != null) {
                if (reported) {
                    registerEvidenceRetention(post, userId, registeredPaths);
                }
                holdLegacyImages(post, userId, reported);
                post.getImages().clear();
            }
            imageService.evictPostImageUrlCache(post.getId());
        }

        for (CheerComment comment : cheerCommentRepo.findAllByAuthor_Id(userId)) {
            comment.setContent(REDACTED_COMMENT);
        }
        int unclaimedAssets = mediaUploadService.deleteOwnedAssetsForAccount(userId, reportedPostIds);
        return unclaimedAssets == 0 ? Result.COMPLETED : Result.ASSET_CLEANUP_PENDING;
    }

    /**
     * Registers each reported image for evidence retention before its row is cleared. The daily discovery job finds
     * images only through post_images, so an image cleared here would stay linked with no retention or review row.
     * The base date follows the same rule as discovery: the first deletion request for evidence that predates the
     * current deletion cycle, otherwise the current cycle start. Without a first request the image goes to review.
     */
    private void registerEvidenceRetention(CheerPost post, Long ownerUserId, Set<String> registeredPaths) {
        UserEntity owner = post.getAuthor();
        LocalDateTime cycleStartedAt = owner == null ? null : owner.getDeletionCycleStartedAt();
        LocalDateTime baseRequestedAt = owner == null ? null : ReportEvidenceRetentionPolicy.evidenceBase(
                owner.getFirstDeletionRequestedAt(),
                cycleStartedAt,
                evidenceRetention.findEarliestReportAt(post.getId()));
        LocalDateTime now = LocalDateTime.now();
        for (PostImage image : post.getImages()) {
            String path = image.getStoragePath();
            if (path == null || !registeredPaths.add(path)
                    || evidenceRetention.existsByStoragePathAndStatusNot(
                            path, ReportEvidenceMediaStatus.RESOLVED_RESTORED)) {
                continue;
            }
            evidenceRetention.save(baseRequestedAt == null
                    ? ReportEvidenceMediaRetention.reviewRequired(image.getId(), post.getId(), ownerUserId, path, now)
                    : ReportEvidenceMediaRetention.activeWithBase(
                            image.getId(), post.getId(), ownerUserId, path, baseRequestedAt, cycleStartedAt, now));
        }
    }

    /**
     * Records each legacy image path before its row is cleared, so reconciliation can still find the stored object
     * after the image and post rows are gone. Managed cheer keys are skipped: media cleanup owns those objects.
     */
    private void holdLegacyImages(CheerPost post, Long ownerUserId, boolean reportProtected) {
        String bucket = storageConfig.getCheerBucket();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        for (PostImage image : post.getImages()) {
            if (MediaDomain.CHEER.isManagedPath(image.getStoragePath())) {
                continue;
            }
            legacyPostImageHolds.save(LegacyPostImageHold.builder()
                    .postImageId(image.getId())
                    .ownerUserId(ownerUserId)
                    .postId(post.getId())
                    .storagePath(image.getStoragePath())
                    .logicalBucket(bucket)
                    .reportProtected(reportProtected)
                    .createdAt(now)
                    .build());
        }
    }
}
