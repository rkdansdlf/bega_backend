package com.example.cheerboard.service;

import com.example.cheerboard.domain.CheerPost;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * 인기 피드 점수 계산기.
 */
@Component
public class PopularFeedScoringService {

    private static final double TIME_DECAY_LIKE_WEIGHT = 3.0;
    private static final double TIME_DECAY_COMMENT_WEIGHT = 2.0;
    private static final double TIME_DECAY_REPOST_WEIGHT = 2.5;
    private static final double TIME_DECAY_VIEW_WEIGHT = 0.2;
    private static final double TIME_DECAY_HALF_LIFE_HOURS = 24.0;

    private static final double ENGAGEMENT_COMMENT_WEIGHT = 2.0;
    private static final double ENGAGEMENT_REPOST_WEIGHT = 3.0;
    private static final double ENGAGEMENT_VIEW_SMOOTHING = 20.0;

    private static final double HYBRID_GLOBAL_WEIGHT = 0.7;
    private static final double HYBRID_TEAM_WEIGHT = 0.2;
    private static final double HYBRID_FOLLOW_WEIGHT = 0.1;
    private static final double HYBRID_GLOBAL_NORMALIZER = 200.0;

    public double calculateTimeDecayScore(CheerPost post, int combinedViews, Instant now) {
        double baseScore = (post.getLikeCount() * TIME_DECAY_LIKE_WEIGHT)
                + (post.getCommentCount() * TIME_DECAY_COMMENT_WEIGHT)
                + (post.getRepostCount() * TIME_DECAY_REPOST_WEIGHT)
                + (combinedViews * TIME_DECAY_VIEW_WEIGHT);

        long ageSeconds = Duration.between(post.getCreatedAt(), now).getSeconds();
        if (ageSeconds < 0) {
            ageSeconds = 0;
        }
        double ageHours = ageSeconds / 3600.0;
        double decay = Math.pow(0.5, ageHours / TIME_DECAY_HALF_LIFE_HOURS);
        return baseScore * decay;
    }

    public double calculateEngagementRateScore(CheerPost post, int combinedViews) {
        double engagement = post.getLikeCount()
                + (post.getCommentCount() * ENGAGEMENT_COMMENT_WEIGHT)
                + (post.getRepostCount() * ENGAGEMENT_REPOST_WEIGHT);
        return engagement / (combinedViews + ENGAGEMENT_VIEW_SMOOTHING);
    }

    public double normalizeGlobalHotScore(double rawGlobalScore) {
        return 1.0 - Math.exp(-(rawGlobalScore / HYBRID_GLOBAL_NORMALIZER));
    }

    public double calculateTeamAffinity(String favoriteTeamId, String postTeamId) {
        if (favoriteTeamId == null || favoriteTeamId.isBlank()) {
            return 0.0;
        }
        if (postTeamId == null || postTeamId.isBlank()) {
            return 0.0;
        }
        return favoriteTeamId.equalsIgnoreCase(postTeamId) ? 1.0 : 0.0;
    }

    public double calculateFollowAffinity(Set<Long> followingIds, Long authorId) {
        if (followingIds == null || followingIds.isEmpty() || authorId == null) {
            return 0.0;
        }
        return followingIds.contains(authorId) ? 1.0 : 0.0;
    }

    public double calculateHybridScore(double normalizedGlobal, double teamAffinity, double followAffinity) {
        return (normalizedGlobal * HYBRID_GLOBAL_WEIGHT)
                + (teamAffinity * HYBRID_TEAM_WEIGHT)
                + (followAffinity * HYBRID_FOLLOW_WEIGHT);
    }
}
