package com.example.leaderboard.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.entity.UserBlock;
import com.example.auth.entity.UserEntity;
import com.example.auth.entity.UserFollow;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.PublicVisibilityVerifier;
import com.example.leaderboard.entity.ScoreEvent;
import com.example.leaderboard.entity.UserScore;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@Import(PublicVisibilityVerifier.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:leaderboard_visibility;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.show_sql=false"
})
class LeaderboardVisibilityRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserScoreRepository userScoreRepository;

    @Autowired
    private ScoreEventRepository scoreEventRepository;

    @Autowired
    private PublicVisibilityVerifier publicVisibilityVerifier;

    @Autowired
    private EntityManager entityManager;

    private UserEntity viewer;
    private UserEntity publicHigh;
    private UserEntity privateHidden;
    private UserEntity blockedPublic;
    private UserEntity privateFollowed;
    private UserEntity publicTieFirst;
    private UserEntity publicTieSecond;

    @BeforeEach
    void setUp() {
        viewer = persistUser("viewer", true);
        publicHigh = persistUser("pub-high", false);
        privateHidden = persistUser("priv-hide", true);
        blockedPublic = persistUser("block-pub", false);
        privateFollowed = persistUser("priv-follow", true);
        publicTieFirst = persistUser("tie-first", false);
        publicTieSecond = persistUser("tie-second", false);

        persistFollow(viewer, privateFollowed);
        persistBlock(blockedPublic, viewer);

        persistScore(publicHigh, 100L, 7);
        persistScore(privateHidden, 99L, 10);
        persistScore(blockedPublic, 98L, 9);
        persistScore(privateFollowed, 95L, 8);
        persistScore(viewer, 90L, 6);
        persistScore(publicTieFirst, 80L, 5);
        persistScore(publicTieSecond, 80L, 4);

        persistEvent(viewer);
        persistEvent(publicHigh);
        persistEvent(privateFollowed);
        persistEvent(blockedPublic);
        persistEvent(privateHidden);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("페이지와 count는 verifier와 동일한 공개 범위를 사용하고 score/userId 순서를 보장한다")
    void visibleLeaderboardUsesVerifierPredicateForPageAndCount() {
        PageRequest firstRequest = PageRequest.of(
                0,
                2,
                Sort.by(Sort.Order.desc("seasonScore"), Sort.Order.asc("userId")));
        Page<UserScore> first = userScoreRepository.findVisibleLeaderboard(viewer.getId(), firstRequest);
        Page<UserScore> second = userScoreRepository.findVisibleLeaderboard(viewer.getId(), firstRequest.next());
        Page<UserScore> third = userScoreRepository.findVisibleLeaderboard(viewer.getId(), firstRequest.next().next());

        List<Long> expectedVisibleIds = List.of(
                publicHigh.getId(),
                privateFollowed.getId(),
                viewer.getId(),
                publicTieFirst.getId(),
                publicTieSecond.getId());
        assertThat(expectedVisibleIds)
                .allMatch(userId -> publicVisibilityVerifier.canAccess(userRepository.findById(userId).orElseThrow(), viewer.getId()));
        assertThat(publicVisibilityVerifier.canAccess(privateHidden, viewer.getId())).isFalse();
        assertThat(publicVisibilityVerifier.canAccess(blockedPublic, viewer.getId())).isFalse();
        assertThat(first.getTotalElements()).isEqualTo(expectedVisibleIds.size());
        assertThat(first.getContent()).extracting(UserScore::getUserId)
                .containsExactly(publicHigh.getId(), privateFollowed.getId());
        assertThat(second.getContent()).extracting(UserScore::getUserId)
                .containsExactly(viewer.getId(), publicTieFirst.getId());
        assertThat(third.getContent()).extracting(UserScore::getUserId)
                .containsExactly(publicTieSecond.getId());
    }

    @Test
    @DisplayName("익명 페이지는 공개 계정만 count하고 비공개 계정을 페이지 밖으로 밀어내지 않는다")
    void anonymousLeaderboardCountsOnlyPublicUsers() {
        Page<UserScore> result = userScoreRepository.findVisibleLeaderboard(
                null,
                PageRequest.of(0, 10, Sort.by(Sort.Order.desc("seasonScore"), Sort.Order.asc("userId"))));

        assertThat(result.getTotalElements()).isEqualTo(4);
        assertThat(result.getContent()).extracting(UserScore::getUserId)
                .containsExactly(
                        publicHigh.getId(),
                        blockedPublic.getId(),
                        publicTieFirst.getId(),
                        publicTieSecond.getId());
    }

    @Test
    @DisplayName("핫 스트릭과 최근 점수는 DB 공개 필터를 limit 전에 적용한다")
    void boundedFeedsFilterVisibilityBeforeLimit() {
        List<UserScore> streaks = userScoreRepository.findVisibleHotStreaks(
                3,
                viewer.getId(),
                PageRequest.of(0, 2));
        List<ScoreEvent> recent = scoreEventRepository.findVisibleRecentScores(
                viewer.getId(),
                PageRequest.of(0, 2));

        assertThat(streaks).extracting(UserScore::getUserId)
                .containsExactly(privateFollowed.getId(), publicHigh.getId());
        assertThat(recent).extracting(ScoreEvent::getUserId)
                .containsExactly(privateFollowed.getId(), publicHigh.getId());
    }

    private UserEntity persistUser(String handle, boolean privateAccount) {
        return userRepository.save(UserEntity.builder()
                .uniqueId(UUID.randomUUID())
                .handle("@" + handle)
                .name(handle)
                .email(handle + "@example.test")
                .role("ROLE_USER")
                .privateAccount(privateAccount)
                .createdAt(LocalDateTime.of(2026, 8, 8, 10, 0))
                .build());
    }

    private void persistFollow(UserEntity follower, UserEntity following) {
        UserFollow follow = new UserFollow();
        follow.setId(new UserFollow.Id(follower.getId(), following.getId()));
        follow.setFollower(follower);
        follow.setFollowing(following);
        entityManager.persist(follow);
    }

    private void persistBlock(UserEntity blocker, UserEntity blocked) {
        UserBlock block = new UserBlock();
        block.setId(new UserBlock.Id(blocker.getId(), blocked.getId()));
        block.setBlocker(blocker);
        block.setBlocked(blocked);
        entityManager.persist(block);
    }

    private void persistScore(UserEntity user, long seasonScore, int currentStreak) {
        userScoreRepository.save(UserScore.builder()
                .userId(user.getId())
                .totalScore(seasonScore)
                .seasonScore(seasonScore)
                .monthlyScore(seasonScore)
                .weeklyScore(seasonScore)
                .currentStreak(currentStreak)
                .build());
    }

    private void persistEvent(UserEntity user) {
        scoreEventRepository.save(ScoreEvent.builder()
                .userId(user.getId())
                .eventType(ScoreEvent.EventType.CORRECT_PREDICTION)
                .baseScore(10)
                .finalScore(10)
                .description("score")
                .build());
    }
}
