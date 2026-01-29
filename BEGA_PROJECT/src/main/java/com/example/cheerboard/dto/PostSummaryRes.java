package com.example.cheerboard.dto;

import java.time.Instant;

public record PostSummaryRes(
                Long id,
                String teamId,
                String teamName,
                String teamShortName,
                String teamColor,
                // title removed
                String content,
                String author,
                Long authorId,
                String authorHandle,
                String authorProfileImageUrl,
                String authorTeamId,
                Instant createdAt,
                int comments,
                int likes,
                boolean liked,
                int views,
                boolean isHot,
                boolean isBookmarked,
                boolean isOwner,
                int repostCount,
                boolean repostedByMe,
                String postType,
                java.util.List<String> imageUrls,
                // 리포스트 관련 필드
                Long repostOfId, // 원본 게시글 ID (리포스트인 경우)
                String repostType, // "SIMPLE", "QUOTE", null(원본)
                EmbeddedPostDto originalPost, // 원본 게시글 임베드 정보
                boolean originalDeleted // 원본 삭제 여부
) {
        /**
         * 기존 생성자와 호환성을 위한 팩토리 메서드 (리포스트가 아닌 경우)
         */
        public static PostSummaryRes of(
                        Long id,
                        String teamId,
                        String teamName,
                        String teamShortName,
                        String teamColor,
                        // title removed
                        String content,
                        String author,
                        Long authorId,
                        String authorHandle,
                        String authorProfileImageUrl,
                        String authorTeamId,
                        Instant createdAt,
                        int comments,
                        int likes,
                        boolean liked,
                        int views,
                        boolean isHot,
                        boolean isBookmarked,
                        boolean isOwner,
                        int repostCount,
                        boolean repostedByMe,
                        String postType,
                        java.util.List<String> imageUrls) {
                return new PostSummaryRes(
                                id, teamId, teamName, teamShortName, teamColor, // title removed,
                                content, author, authorId, authorHandle,
                                authorProfileImageUrl, authorTeamId, createdAt,
                                comments, likes, liked, views, isHot, isBookmarked,
                                isOwner, repostCount, repostedByMe, postType, imageUrls,
                                null, null, null, false);
        }
}
