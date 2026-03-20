package com.example.cheerboard.service;

import com.example.cheerboard.domain.CheerPost;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static com.example.cheerboard.service.CheerServiceConstants.HOT_BADGE_THRESHOLD;

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

    private static final double HYBRID_GLOBAL_WEIGHT = 0.55;
    private static final double HYBRID_ENGAGEMENT_WEIGHT = 0.15;
    private static final double HYBRID_TEAM_WEIGHT = 0.2;
    private static final double HYBRID_FOLLOW_WEIGHT = 0.1;
    private static final double HYBRID_GLOBAL_NORMALIZER = 200.0;

    private static final double FRESHNESS_MAX_BOOST = 1.5;
    private static final long FRESHNESS_WINDOW_MINUTES = 120;
    private static final double TEAM_AFFINITY_DEFAULT = 0.3;

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

    public double calculateGlobalHotBaseScore(CheerPost post, int combinedViews, Instant now) {
        double timeDecayScore = calculateTimeDecayScore(post, combinedViews, now);
        return normalizeGlobalHotScore(timeDecayScore);
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
            return TEAM_AFFINITY_DEFAULT;
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

    public double calculateHybridScore(double normalizedGlobal, double normalizedEngagement,
            double teamAffinity, double followAffinity, double freshnessBoost) {
        double base = (normalizedGlobal * HYBRID_GLOBAL_WEIGHT)
                + (normalizedEngagement * HYBRID_ENGAGEMENT_WEIGHT)
                + (teamAffinity * HYBRID_TEAM_WEIGHT)
                + (followAffinity * HYBRID_FOLLOW_WEIGHT);
        return base * freshnessBoost;
    }

    /**
     * @deprecated Use
     *             {@link #calculateHybridScore(double, double, double, double, double)}
     *             instead.
     */
    @Deprecated
    public double calculateHybridScore(double normalizedGlobal, double teamAffinity, double followAffinity) {
        return calculateHybridScore(normalizedGlobal, 0.0, teamAffinity, followAffinity, 1.0);
    }

    public double calculateFreshnessBoost(java.time.Instant createdAt, java.time.Instant now) {
        long ageMinutes = Duration.between(createdAt, now).toMinutes();
        if (ageMinutes < 0)
            ageMinutes = 0;
        if (ageMinutes <= FRESHNESS_WINDOW_MINUTES) {
            return FRESHNESS_MAX_BOOST - ((FRESHNESS_MAX_BOOST - 1.0) * ageMinutes / FRESHNESS_WINDOW_MINUTES);
        }
        return 1.0;
    }

    public boolean isHotEligible(CheerPost post, int combinedViews, Instant now) {
        return calculateGlobalHotBaseScore(post, combinedViews, now) >= HOT_BADGE_THRESHOLD;
    }

    public boolean isHotEligible(double baseScore) {
        return baseScore >= HOT_BADGE_THRESHOLD;
    }
}
