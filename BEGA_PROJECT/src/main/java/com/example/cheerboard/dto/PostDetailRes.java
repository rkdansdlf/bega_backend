package com.example.cheerboard.dto;

import java.time.Instant;
import java.util.List;

public record PostDetailRes(
                Long id,
                String teamId,
                String teamName,
                String teamShortName,
                String teamColor,
                String title,
                String content,
                String author,
                Long authorId,
                String authorHandle,
                String authorEmail,
                String authorProfileImageUrl,
                Instant createdAt,
                int comments,
                int likes,
                boolean likedByMe,
                boolean isBookmarked,
                boolean isOwner,
                List<String> imageUrls,
                Integer views,
                int repostCount,
                boolean repostedByMe,
                String postType,
                // 리포스트 관련 필드
                Long repostOfId,           // 원본 게시글 ID (리포스트인 경우)
                String repostType,         // "SIMPLE", "QUOTE", null(원본)
                EmbeddedPostDto originalPost,  // 원본 게시글 임베드 정보
                boolean originalDeleted    // 원본 삭제 여부
) {
    /**
     * 기존 생성자와 호환성을 위한 팩토리 메서드 (리포스트가 아닌 경우)
     */
    public static PostDetailRes of(
            Long id,
            String teamId,
            String teamName,
            String teamShortName,
            String teamColor,
            String title,
            String content,
            String author,
            Long authorId,
            String authorHandle,
            String authorEmail,
            String authorProfileImageUrl,
            Instant createdAt,
            int comments,
            int likes,
            boolean likedByMe,
            boolean isBookmarked,
            boolean isOwner,
            List<String> imageUrls,
            Integer views,
            int repostCount,
            boolean repostedByMe,
            String postType
    ) {
        return new PostDetailRes(
                id, teamId, teamName, teamShortName, teamColor,
                title, content, author, authorId, authorHandle,
                authorEmail, authorProfileImageUrl, createdAt,
                comments, likes, likedByMe, isBookmarked, isOwner,
                imageUrls, views, repostCount, repostedByMe, postType,
                null, null, null, false
        );
    }
}
