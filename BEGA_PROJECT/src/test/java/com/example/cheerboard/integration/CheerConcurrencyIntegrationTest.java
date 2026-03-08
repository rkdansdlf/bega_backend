package com.example.cheerboard.integration;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.dto.CreatePostReq;
import com.example.cheerboard.entity.CheerVoteEntity;
import com.example.cheerboard.entity.CheerVoteId;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repository.CheerVoteRepository;
import com.example.cheerboard.service.CheerBattleService;
import com.example.cheerboard.service.CheerInteractionService;
import com.example.cheerboard.service.CheerPostService;
import com.example.kbo.entity.TeamEntity;
import com.example.kbo.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:cheer_concurrency;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=30000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=false",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=6379",
        "spring.data.redis.repositories.enabled=false",
        "storage.type=oci",
        "oci.s3.endpoint=http://localhost:4566",
        "oci.s3.access-key=test-access-key",
        "oci.s3.secret-key=test-secret-key",
        "oci.s3.bucket=test-bucket",
        "oci.s3.region=ap-seoul-1",
        "spring.autoconfigure.exclude=io.awspring.cloud.autoconfigure.s3.S3AutoConfiguration"
})
class CheerConcurrencyIntegrationTest {

    @Autowired
    private CheerInteractionService interactionService;

    @Autowired
    private CheerPostService postService;

    @Autowired
    private CheerBattleService battleService;

    @Autowired
    private CheerPostRepo postRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private TeamRepository teamRepo;

    @Autowired
    private CheerVoteRepository voteRepository;

    private UserEntity author;
    private TeamEntity team;

    @BeforeEach
    void setUp() {
        String setupSuffix = UUID.randomUUID().toString().substring(0, 8);

        if (!teamRepo.existsById("LG")) {
            team = TeamEntity.builder().teamId("LG").teamName("LG Twins").teamShortName("LG").city("Seoul").build();
            teamRepo.save(team);
        } else {
            team = teamRepo.findById("LG").orElseThrow();
        }

        author = UserEntity.builder()
                .email("testauthor+" + setupSuffix + "@test.com")
                .name("Author")
                .handle("a" + setupSuffix)
                .uniqueId(UUID.randomUUID())
                .provider("LOCAL")
                .role("ROLE_USER")
                .build();
        userRepo.save(author);
    }

    @Test
    @DisplayName("L-05: Concurrent likes race condition should increment accurately")
    void testConcurrentLikes() throws InterruptedException {
        // Given
        CheerPost post = CheerPost.builder()
                .author(author)
                .team(team)
                .content("Test Content")
                .postType(PostType.NORMAL)
                .shareMode(CheerPost.ShareMode.INTERNAL_REPOST)
                .likeCount(0)
                .build();
        post = postRepo.saveAndFlush(post);

        int threadCount = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        String runSuffix = UUID.randomUUID().toString().substring(0, 8);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        List<UserEntity> likers = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            UserEntity u = UserEntity.builder()
                    .email("liker" + i + "+" + runSuffix + "@test.com")
                    .name("Liker " + i)
                    .provider("LOCAL")
                    .handle("lk" + runSuffix.substring(0, 4) + i)
                    .uniqueId(UUID.randomUUID())
                    .role("ROLE_USER")
                    .build();
            likers.add(userRepo.save(u));
        }

        Long postId = post.getId();

        // When
        for (int i = 0; i < threadCount; i++) {
            final UserEntity liker = likers.get(i);
            executorService.submit(() -> {
                try {
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(liker.getId(), null, List.of()));
                    interactionService.toggleLike(postId, liker);
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    SecurityContextHolder.clearContext();
                    latch.countDown();
                }
            });
        }
        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);
        assertThat(errors).isEmpty();

        // Then
        CheerPost updatedPost = postRepo.findById(postId).orElseThrow();
        assertThat(updatedPost.getLikeCount()).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("R-09: Concurrent reposts should keep repostCount accurate")
    void testConcurrentReposts() throws InterruptedException {
        // Given
        CheerPost original = CheerPost.builder()
                .author(author)
                .team(team)
                .content("Original Content")
                .postType(PostType.NORMAL)
                .shareMode(CheerPost.ShareMode.INTERNAL_REPOST)
                .repostCount(0)
                .build();
        original = postRepo.saveAndFlush(original);

        int threadCount = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        String runSuffix = UUID.randomUUID().toString().substring(0, 8);

        List<UserEntity> reposters = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            UserEntity u = UserEntity.builder()
                    .email("reposter" + i + "+" + runSuffix + "@test.com")
                    .name("Reposter " + i)
                    .provider("LOCAL")
                    .handle("rp" + runSuffix.substring(0, 4) + i)
                    .uniqueId(UUID.randomUUID())
                    .role("ROLE_USER")
                    .build();
            reposters.add(userRepo.save(u));
        }

        Long originalId = original.getId();

        // When
        for (int i = 0; i < threadCount; i++) {
            final UserEntity reposter = reposters.get(i);
            executorService.submit(() -> {
                try {
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(reposter.getId(), null, List.of()));
                    postService.toggleRepost(originalId, reposter);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    SecurityContextHolder.clearContext();
                    latch.countDown();
                }
            });
        }
        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        // Then
        CheerPost updatedPost = postRepo.findById(originalId).orElseThrow();
        assertThat(updatedPost.getRepostCount()).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("P-01: Blank-only content should be rejected")
    void testBlankContentRejected() {
        // Given
        CreatePostReq req = new CreatePostReq("LG", "   ", List.of(), "NORMAL");

        // When & Then
        assertThatThrownBy(() -> postService.createPost(req, author))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("CB-05: Concurrent battle votes should accurately increment vote count")
    void testConcurrentBattleVotes() throws InterruptedException {
        // Given
        String gameId = "game_test_" + UUID.randomUUID().toString().substring(0, 8);
        String teamId = "LG";

        CheerVoteEntity voteEntity = CheerVoteEntity.builder()
                .gameId(gameId)
                .teamId(teamId)
                .voteCount(0)
                .build();
        voteRepository.saveAndFlush(voteEntity);

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        String runSuffix = UUID.randomUUID().toString().substring(0, 8);

        List<UserEntity> voters = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            UserEntity u = UserEntity.builder()
                    .email("voter" + i + "+" + runSuffix + "@test.com")
                    .name("Voter " + i)
                    .provider("LOCAL")
                    .handle("vt" + runSuffix.substring(0, 4) + i)
                    .uniqueId(UUID.randomUUID())
                    .cheerPoints(10) // Enough points
                    .role("ROLE_USER")
                    .build();
            voters.add(userRepo.save(u));
        }

        // When
        for (int i = 0; i < threadCount; i++) {
            final UserEntity voter = voters.get(i);
            executorService.submit(() -> {
                try {
                    battleService.vote(gameId, teamId, voter.getId());
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        // Then
        CheerVoteEntity updatedVote = voteRepository
                .findById(CheerVoteId.builder().gameId(gameId).teamId(teamId).build()).orElseThrow();
        assertThat(updatedVote.getVoteCount()).isEqualTo(threadCount);
    }
}
