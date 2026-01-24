package com.example.cheerboard.dto;

import java.time.Instant;
import java.util.List;

/**
 * 리포스트 시 원본 게시글을 임베드 형태로 표시하기 위한 DTO
 * 피드에서 축소된 카드 형태로 원본 게시글을 표시할 때 사용
 */
public record EmbeddedPostDto(
        Long id,
        String teamId,
        String teamColor,
        String title,
        String content, // 100자 미리보기
        String author,
        String authorHandle,
        String authorProfileImageUrl,
        Instant createdAt,
        List<String> imageUrls,
        boolean deleted, // 원본 삭제 여부 ("삭제된 게시글입니다" 표시용)
        int likeCount,
        int commentCount,
        int repostCount) {
    /**
     * 삭제된 게시글을 위한 플레이스홀더 생성
     */
    public static EmbeddedPostDto deletedPlaceholder(Long originalPostId) {
        return new EmbeddedPostDto(
                originalPostId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                true,
                0,
                0,
                0);
    }

    /**
     * 컨텐츠를 100자로 제한하는 팩토리 메서드
     */
    public static EmbeddedPostDto of(
            Long id,
            String teamId,
            String teamColor,
            String title,
            String content,
            String author,
            String authorHandle,
            String authorProfileImageUrl,
            Instant createdAt,
            List<String> imageUrls,
            int likeCount,
            int commentCount,
            int repostCount) {
        String truncatedContent = content != null && content.length() > 100
                ? content.substring(0, 100) + "..."
                : content;

        return new EmbeddedPostDto(
                id,
                teamId,
                teamColor,
                title,
                truncatedContent,
                author,
                authorHandle,
                authorProfileImageUrl,
                createdAt,
                imageUrls != null ? imageUrls : List.of(),
                false,
                likeCount,
                commentCount,
                repostCount);
    }
}
