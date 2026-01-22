package com.example.cheerboard.dto;

import java.time.Instant;

public record PostSummaryRes(
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
        String postType,
        java.util.List<String> imageUrls) {
}
