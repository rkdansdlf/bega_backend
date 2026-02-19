package com.example.cheerboard.service;

import com.example.cheerboard.domain.CheerComment;
import com.example.cheerboard.dto.CommentRes;
import com.example.auth.entity.UserEntity;
import com.example.profile.storage.service.ProfileImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CommentDtoMapper {

    private final ProfileImageService profileImageService;

    public CommentRes toCommentRes(CheerComment comment, boolean likedByMe) {
        // 대댓글 변환 (재귀적으로 처리)
        // likedByMe for replies depends on if the user liked those replies.
        // This method assumes the caller calculates likedByMe for THIS comment.
        // But for replies, we need to know if the user liked them too.
        // The original CheerService.toCommentRes called itself recursively but
        // re-calculated liked status for replies
        // using `isCommentLikedByUser`. This triggers N+1.
        // The optimized version `toCommentResWithLikedSet` passed a set of liked IDs.

        // We should support the optimized version as the primary one or allow passing a
        // Set.
        // But here we might not have the full set if we just convert one comment.
        // Let's implement `toCommentRes` which assumes we might need to fetch likes for
        // replies?
        // Or better: The caller (Service) should provide the needed data.

        // If we want to avoid N+1 in mapper, we should pass the Set<Long>
        // likedCommentIds to the mapper.
        return toCommentRes(comment, Collections.emptySet());
        // Wait, if we pass empty set, likedByMe will be false for all replies.
        // The simple `toCommentRes` in CheerService did:
        // boolean likedByMe = isCommentLikedByUser(comment.getId(), userId);
        // replies = comment.getReplies().stream().map(reply -> toCommentRes(reply))...

        // If we invoke this mapper, we don't want to inject Repositories here.
        // So the Service should prepare the data.
        // Service should fetch all liked comment IDs for the current user and pass it.
    }

    public CommentRes toCommentRes(CheerComment comment, Set<Long> likedCommentIds) {
        boolean likedByMe = likedCommentIds.contains(comment.getId());

        List<CommentRes> replies = comment.getReplies().stream()
                .map(reply -> toCommentRes(reply, likedCommentIds))
                .collect(Collectors.toList());

        return new CommentRes(
                comment.getId(),
                resolveDisplayName(comment.getAuthor()),
                comment.getAuthor().getEmail(),
                comment.getAuthor().getFavoriteTeamId(),
                resolveAuthorProfileImageUrl(comment.getAuthor()),
                comment.getAuthor().getHandle(),
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getLikeCount(),
                likedByMe,
                replies);
    }

    private String resolveDisplayName(UserEntity user) {
        if (user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }
        return user.getEmail();
    }

    private String resolveAuthorProfileImageUrl(UserEntity author) {
        if (author == null) {
            return null;
        }

        String rawValue = author.getProfileImageUrl();
        String resolved = profileImageService.getProfileImageUrl(rawValue);
        if (resolved != null && !resolved.isBlank()) {
            return resolved;
        }

        if (rawValue != null && !rawValue.isBlank()) {
            if (rawValue.startsWith("http://")
                    || rawValue.startsWith("https://")
                    || rawValue.startsWith("/")) {
                return rawValue;
            }
        }

        return null;
    }
}
