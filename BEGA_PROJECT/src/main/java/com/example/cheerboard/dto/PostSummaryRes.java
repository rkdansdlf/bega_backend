package com.example.cheerboard.dto;

import java.time.Instant;

public record PostSummaryRes(
                Long id,
                String teamId,
                String teamName,
                String teamShortName,
                String teamColor,
                String title,
                String author,
                String authorProfileImageUrl,
                String authorTeamId,
                Instant createdAt,
                int comments,
                int likes,
                int views,
                boolean isHot,
                boolean isBookmarked,
                boolean isOwner,
                String postType,
                java.util.List<String> imageUrls) {
}
