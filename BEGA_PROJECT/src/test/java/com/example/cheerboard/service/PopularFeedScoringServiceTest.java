package com.example.cheerboard.service;

import com.example.cheerboard.domain.CheerPost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PopularFeedScoringServiceTest {

    private final PopularFeedScoringService scoringService = new PopularFeedScoringService();

    @Test
    @DisplayName("TIME_DECAY 점수는 기준 점수에 시간 감쇠를 적용한다")
    void calculateTimeDecayScore_appliesDecay() {
        CheerPost post = CheerPost.builder()
                .likeCount(4)
                .commentCount(2)
                .repostCount(1)
                .createdAt(Instant.parse("2026-02-12T00:00:00Z"))
                .build();
        Instant now = Instant.parse("2026-02-12T00:00:00Z");

        double score = scoringService.calculateTimeDecayScore(post, 10, now);
        // base = 4*3 + 2*2 + 1*2.5 + 10*0.2 = 20.5, decay(0h)=1
        assertThat(score).isCloseTo(20.5, within(0.0001));
    }

    @Test
    @DisplayName("ENGAGEMENT_RATE 점수는 조회수 보정 분모를 사용한다")
    void calculateEngagementRateScore_usesViewSmoothing() {
        CheerPost post = CheerPost.builder()
                .likeCount(10)
                .commentCount(3)
                .repostCount(2)
                .build();

        double score = scoringService.calculateEngagementRateScore(post, 80);
        // (10 + 3*2 + 2*3) / (80 + 20) = 22 / 100 = 0.22
        assertThat(score).isCloseTo(0.22, within(0.0001));
    }

    @Test
    @DisplayName("HYBRID 점수는 global/engagement/team/follow 가중치와 freshness를 합산한다")
    void calculateHybridScore_combinesWeights() {
        double hybrid = scoringService.calculateHybridScore(0.7, 0.5, 1.0, 1.0, 1.0);
        // 0.7*0.55 + 0.5*0.15 + 1.0*0.2 + 1.0*0.1 = 0.385 + 0.075 + 0.2 + 0.1 = 0.76
        assertThat(hybrid).isCloseTo(0.76, within(0.0001));
    }

    @Test
    @DisplayName("팀/팔로우 친화도는 조건 만족 시 1.0을 반환한다")
    void affinityScores_returnOneWhenMatched() {
        assertThat(scoringService.calculateTeamAffinity("LG", "lg")).isEqualTo(1.0);
        assertThat(scoringService.calculateFollowAffinity(Set.of(3L, 5L), 5L)).isEqualTo(1.0);
    }

    // ── 아래부터 신규 테스트 ──

    @Test
    @DisplayName("TIME_DECAY: 24시간 후 점수는 약 50% 감소한다")
    void calculateTimeDecayScore_halfLifeAt24Hours() {
        Instant created = Instant.parse("2026-03-01T00:00:00Z");
        CheerPost post = CheerPost.builder()
                .likeCount(10)
                .commentCount(5)
                .repostCount(2)
                .createdAt(created)
                .build();

        Instant atCreation = created;
        Instant after24h = created.plus(24, ChronoUnit.HOURS);

        double scoreAtCreation = scoringService.calculateTimeDecayScore(post, 100, atCreation);
        double scoreAfter24h = scoringService.calculateTimeDecayScore(post, 100, after24h);

        // half-life = 24h → after 24h, score should be ~50% of original
        double ratio = scoreAfter24h / scoreAtCreation;
        assertThat(ratio).isCloseTo(0.5, within(0.01));
    }

    @Test
    @DisplayName("ENGAGEMENT_RATE: 조회수 0일 때 NaN이나 Infinity가 아닌 유한 값을 반환한다")
    void calculateEngagementRateScore_zeroViewsSafe() {
        CheerPost post = CheerPost.builder()
                .likeCount(5)
                .commentCount(2)
                .repostCount(1)
                .build();

        double score = scoringService.calculateEngagementRateScore(post, 0);
        // (5 + 2*2 + 1*3) / (0 + 20) = 12 / 20 = 0.6
        assertThat(score).isFinite();
        assertThat(score).isCloseTo(0.6, within(0.0001));
    }

    @Test
    @DisplayName("FRESHNESS_BOOST: 0분=1.5, 60분=1.25, 120분=1.0, 180분=1.0")
    void calculateFreshnessBoost_linearDecay() {
        Instant created = Instant.parse("2026-03-01T00:00:00Z");

        assertThat(scoringService.calculateFreshnessBoost(created, created))
                .isCloseTo(1.5, within(0.001));
        assertThat(scoringService.calculateFreshnessBoost(created, created.plus(60, ChronoUnit.MINUTES)))
                .isCloseTo(1.25, within(0.001));
        assertThat(scoringService.calculateFreshnessBoost(created, created.plus(120, ChronoUnit.MINUTES)))
                .isCloseTo(1.0, within(0.001));
        assertThat(scoringService.calculateFreshnessBoost(created, created.plus(180, ChronoUnit.MINUTES)))
                .isCloseTo(1.0, within(0.001));
    }

    @Test
    @DisplayName("TEAM_AFFINITY: 팀 미설정=0.3, 같은 팀=1.0, 다른 팀=0.0, 게시글 팀 없음=0.0")
    void calculateTeamAffinity_graduated() {
        // No favorite team → default interest
        assertThat(scoringService.calculateTeamAffinity(null, "KIA")).isEqualTo(0.3);
        assertThat(scoringService.calculateTeamAffinity("", "KIA")).isEqualTo(0.3);
        // Same team
        assertThat(scoringService.calculateTeamAffinity("KIA", "kia")).isEqualTo(1.0);
        // Different team
        assertThat(scoringService.calculateTeamAffinity("LG", "KIA")).isEqualTo(0.0);
        // Post has no team
        assertThat(scoringService.calculateTeamAffinity("LG", null)).isEqualTo(0.0);
        assertThat(scoringService.calculateTeamAffinity("LG", "")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("HYBRID 랭킹: 팀 일치 + 최신 글 > 높은 global 점수의 오래된 글")
    void hybridRanking_freshSameTeamBeatsOldHighGlobal() {
        // Post A: old, high global, different team
        double hybridA = scoringService.calculateHybridScore(
                0.9, // high global
                0.3, // moderate engagement
                0.0, // different team
                0.0, // not followed
                1.0 // >2h old, no boost
        );
        // Post B: fresh, moderate global, same team
        double hybridB = scoringService.calculateHybridScore(
                0.4, // moderate global
                0.5, // good engagement
                1.0, // same team
                0.0, // not followed
                1.5 // just posted, max boost
        );

        assertThat(hybridB).isGreaterThan(hybridA);
    }
}
