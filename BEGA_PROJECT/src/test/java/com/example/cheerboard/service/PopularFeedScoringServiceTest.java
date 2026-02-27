package com.example.cheerboard.service;

import com.example.cheerboard.domain.CheerPost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
    @DisplayName("HYBRID 점수는 global/team/follow 가중치를 합산한다")
    void calculateHybridScore_combinesWeights() {
        double hybrid = scoringService.calculateHybridScore(0.7, 1.0, 1.0);
        // 0.7*0.7 + 1.0*0.2 + 1.0*0.1 = 0.79
        assertThat(hybrid).isCloseTo(0.79, within(0.0001));
    }

    @Test
    @DisplayName("팀/팔로우 친화도는 조건 만족 시 1.0을 반환한다")
    void affinityScores_returnOneWhenMatched() {
        assertThat(scoringService.calculateTeamAffinity("LG", "lg")).isEqualTo(1.0);
        assertThat(scoringService.calculateFollowAffinity(Set.of(3L, 5L), 5L)).isEqualTo(1.0);
    }
}
