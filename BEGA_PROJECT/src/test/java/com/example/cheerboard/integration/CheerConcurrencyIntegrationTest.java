package com.example.cheerboard.integration;

import com.example.BegaDiary.Entity.BegaDiary;
import com.example.BegaDiary.Repository.BegaDiaryRepository;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.dto.CreateCommentReq;
import com.example.cheerboard.dto.CreatePostReq;
import com.example.cheerboard.service.CheerPostCreationResult;
import com.example.cheerboard.entity.CheerVoteEntity;
import com.example.cheerboard.entity.CheerVoteId;
import com.example.cheerboard.exception.DuplicateCommentException;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repository.CheerVoteRepository;
import com.example.cheerboard.service.CheerBattleService;
import com.example.cheerboard.service.CheerCommentService;
import com.example.cheerboard.service.CheerInteractionService;
import com.example.cheerboard.service.CheerPostService;
import com.example.cheerboard.service.CheerService;
import com.example.kbo.entity.GameEntity;
import com.example.kbo.entity.TeamEntity;
import com.example.kbo.repository.GameRepository;
import com.example.kbo.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
        "storage.type=oci",
        "oci.s3.endpoint=http://localhost:4566",
        "oci.s3.access-key=test-access-key",
        "oci.s3.secret-key=test-secret-key",
        "oci.s3.bucket=test-bucket",
        "oci.s3.region=ap-seoul-1"
})
class CheerConcurrencyIntegrationTest {

    @Autowired
    private CheerInteractionService interactionService;

    @Autowired
    private CheerPostService postService;

    @Autowired
    private CheerService cheerService;

    @Autowired
    private CheerBattleService battleService;

    @Autowired
    private CheerCommentService commentService;

    @Autowired
    private CheerPostRepo postRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private TeamRepository teamRepo;

    @Autowired
    private CheerVoteRepository voteRepository;

    @Autowired
    private BegaDiaryRepository diaryRepository;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_task4_test_cheer_diary ON cheer_post(diary_id)");
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
    @DisplayName("Two independent create transactions converge on one active linked post")
    void concurrentLinkedCreate_returnsSameWinnerId() throws InterruptedException {
        author.setFavoriteTeam(team);
        author = userRepo.saveAndFlush(author);
        BegaDiary diary = persistEligibleDiary(author);
        CreatePostReq req = new CreatePostReq(
                "LG", "동시 직관 인증", List.of(), "CHECKIN", null,
                null, null, null, null, null, null, null, diary.getId(), null);
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        List<CheerPostCreationResult> results = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        Long authorId = author.getId();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    RequestContextHolder.setRequestAttributes(
                            new ServletRequestAttributes(new MockHttpServletRequest()));
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(authorId, null, List.of()));
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    results.add(cheerService.createPost(req));
                } catch (Throwable throwable) {
                    errors.add(throwable);
                } finally {
                    SecurityContextHolder.clearContext();
                    RequestContextHolder.resetRequestAttributes();
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(errors).isEmpty();
        assertThat(results).hasSize(2);
        assertThat(results).extracting(result -> result.post().id()).containsOnly(results.get(0).post().id());
        assertThat(results).extracting(CheerPostCreationResult::created).containsExactlyInAnyOrder(true, false);
        CheerPost active = postRepo.findFirstByDiaryIdAndDeletedFalse(diary.getId()).orElseThrow();
        assertThat(active.getId()).isEqualTo(results.get(0).post().id());
        assertThat(postRepo.findAll().stream()
                .filter(post -> diary.getId().equals(post.getDiaryId()))
                .count()).isEqualTo(1L);
    }

    @Test
    @org.junit.jupiter.api.Disabled("H2 in-memory does not replicate PostgreSQL row-locking for concurrent duplicate detection")
    @DisplayName("C-11: Concurrent duplicate comments should allow only one save")
    void testConcurrentDuplicateComments() throws InterruptedException {
        CheerPost post = CheerPost.builder()
                .author(author)
                .team(team)
                .content("Comment Target")
                .postType(PostType.NORMAL)
                .build();
        post = postRepo.saveAndFlush(post);

        String runSuffix = UUID.randomUUID().toString().substring(0, 8);
        UserEntity commenter = UserEntity.builder()
                .email("commenter+" + runSuffix + "@test.com")
                .name("Commenter")
                .handle("cm" + runSuffix)
                .uniqueId(UUID.randomUUID())
                .provider("LOCAL")
                .role("ROLE_USER")
                .favoriteTeam(team)
                .build();
        commenter = userRepo.save(commenter);
        final UserEntity finalCommenter = commenter;

        int threadCount = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        List<Long> successIds = Collections.synchronizedList(new ArrayList<>());

        Long postId = post.getId();
        CreateCommentReq req = new CreateCommentReq("같은 댓글");

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(finalCommenter.getId(), null, List.of()));
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    successIds.add(commentService.addComment(postId, req, finalCommenter).id());
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    SecurityContextHolder.clearContext();
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(successIds).hasSize(1);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).isInstanceOf(DuplicateCommentException.class);
        assertThat(postRepo.findById(postId).orElseThrow().getCommentCount()).isEqualTo(1);
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

    private BegaDiary persistEligibleDiary(UserEntity owner) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        GameEntity game = gameRepository.save(GameEntity.builder()
                .gameId("T4" + suffix)
                .gameDate(LocalDate.of(2026, 7, 13))
                .homeTeam("LG")
                .awayTeam("KIA")
                .stadium("잠실")
                .build());
        return diaryRepository.saveAndFlush(BegaDiary.builder()
                .diaryDate(game.getGameDate())
                .game(game)
                .memo("internal test fixture")
                .mood(BegaDiary.DiaryEmoji.HAPPY)
                .type(BegaDiary.DiaryType.ATTENDED)
                .winning(BegaDiary.DiaryWinning.WIN)
                .photoUrls(List.of())
                .user(owner)
                .team("LG")
                .stadium("잠실")
                .ticketVerified(true)
                .ticketVerifiedAt(LocalDateTime.now())
                .build());
    }
}
