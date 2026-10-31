package com.example.auth.service;

import com.example.cheerboard.domain.CheerComment;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.repo.CheerCommentRepo;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.media.entity.MediaDomain;
import com.example.media.service.MediaUploadService;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Redacts authored public text/media pointers while preserving unresolved moderation evidence. */
@Service
@RequiredArgsConstructor
public class AccountDeletionContentService {

    private static final String REDACTED_POST = "게시글이 비식별 처리되었습니다.";
    private static final String REDACTED_COMMENT = "댓글이 비식별 처리되었습니다.";

    private final CheerPostRepo cheerPostRepo;
    private final CheerCommentRepo cheerCommentRepo;
    private final CheerReportRepo cheerReportRepo;
    private final MediaUploadService mediaUploadService;

    @Transactional
    public void anonymize(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("A persisted account ID is required for finalization.");
        }
        var authoredPosts = cheerPostRepo.findAllByAuthorIdIncludingDeletedForUpdate(userId);
        Set<Long> queryProtectedPostIds = cheerReportRepo.findOpenModerationPostIdsByAuthorId(userId);
        Set<Long> protectedPostIds = queryProtectedPostIds == null ? Set.of() : queryProtectedPostIds;

        for (CheerPost post : authoredPosts) {
            if (protectedPostIds.contains(post.getId())) {
                continue;
            }
            mediaUploadService.unlinkEntityMedia(MediaDomain.CHEER, post.getId());
            post.setContent(REDACTED_POST);
            post.setSourceUrl(null);
            post.setSourceTitle(null);
            post.setSourceAuthor(null);
            post.setSourceLicense(null);
            post.setSourceLicenseUrl(null);
            post.setSourceChangedNote(null);
            post.setSourceSnapshotType(null);
            if (post.getImages() != null) {
                post.getImages().clear();
            }
        }

        for (CheerComment comment : cheerCommentRepo.findAllByAuthor_Id(userId)) {
            if (comment.getPost() == null || !protectedPostIds.contains(comment.getPost().getId())) {
                comment.setContent(REDACTED_COMMENT);
            }
        }
        mediaUploadService.deleteOwnedAssetsForAccount(userId, protectedPostIds);
    }
}
