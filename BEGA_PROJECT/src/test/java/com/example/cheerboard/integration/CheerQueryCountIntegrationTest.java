package com.example.cheerboard.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.auth.entity.UserEntity;
import com.example.BegaDiary.Entity.BegaDiary;
import com.example.BegaDiary.Repository.BegaDiaryRepository;
import com.example.auth.repository.UserBlockRepository;
import com.example.auth.repository.UserFollowRepository;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.BlockService;
import com.example.auth.service.FollowService;
import com.example.auth.service.PublicVisibilityVerifier;
import com.example.auth.service.UserService;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.CheerPostBookmark;
import com.example.cheerboard.domain.CheerPostLike;
import com.example.cheerboard.domain.CheerPostRepost;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.repo.CheerBookmarkRepo;
import com.example.cheerboard.repo.CheerCommentLikeRepo;
import com.example.cheerboard.repo.CheerCommentRepo;
import com.example.cheerboard.repo.CheerPostLikeRepo;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerPostRepostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.cheerboard.service.CheerFeedService;
import com.example.cheerboard.service.CheerInteractionService;
import com.example.cheerboard.service.CheerLinkedPostService;
import com.example.cheerboard.service.CheerMonitoringMetricsService;
import com.example.cheerboard.service.CheerPostService;
import com.example.cheerboard.service.HotPostChecker;
import com.example.cheerboard.service.PermissionValidator;
import com.example.cheerboard.service.PopularFeedScoringService;
import com.example.cheerboard.service.PostDtoMapper;
import com.example.cheerboard.service.RedisPostService;
import com.example.cheerboard.storage.service.ImageService;
import com.example.kbo.entity.TeamEntity;
import com.example.kbo.entity.GameEntity;
import com.example.mate.entity.Party;
import com.example.mate.repository.PartyRepository;
import com.example.notification.service.NotificationService;
import com.example.profile.storage.service.ProfileImageService;
import com.example.support.HibernateQueryCountSupport;
import com.example.support.HibernateStatisticsTestConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@Import(HibernateStatisticsTestConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:cheer_query_count;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.show_sql=false",
        "logging.level.org.hibernate.SQL=ERROR",
        "logging.level.org.hibernate.orm.jdbc.bind=ERROR"
})
class CheerQueryCountIntegrationTest {

    private static final long FULL_FEED_MAX_QUERIES = 6;
    private static final long LIGHTWEIGHT_FEED_MAX_QUERIES = 2;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserBlockRepository userBlockRepository;

    @Autowired
    private UserFollowRepository userFollowRepository;

    @Autowired
    private CheerPostRepo postRepo;

    @Autowired
    private CheerPostLikeRepo likeRepo;

    @Autowired
    private CheerBookmarkRepo bookmarkRepo;

    @Autowired
    private CheerPostRepostRepo repostRepo;

    @Autowired
    private CheerCommentLikeRepo commentLikeRepo;

    @Autowired
    private CheerCommentRepo commentRepo;

    @Autowired
    private CheerReportRepo reportRepo;

    @Autowired
    private BegaDiaryRepository diaryRepository;

    @Autowired
    private PartyRepository partyRepository;

    private CheerFeedService feedService;
    private ImageService imageService;
    private RedisPostService redisPostService;
    private ProfileImageService profileImageService;

    @BeforeEach
    void setUp() {
        imageService = mock(ImageService.class);
        redisPostService = mock(RedisPostService.class);
        profileImageService = mock(ProfileImageService.class);
        PopularFeedScoringService scoringService = mock(PopularFeedScoringService.class);
        FollowService followService = mock(FollowService.class);
        BlockService blockService = mock(BlockService.class);
        UserService userService = mock(UserService.class);
        NotificationService notificationService = mock(NotificationService.class);
        CheerPostService postService = mock(CheerPostService.class);

        when(imageService.getPostImageUrlsByPostIds(anyList())).thenReturn(Collections.emptyMap());
        when(redisPostService.getViewCounts(anyCollection())).thenReturn(Collections.emptyMap());
        when(profileImageService.getProfileImageUrlForCheerFeed(anyLong(), any(), any())).thenReturn(null);
        when(scoringService.isHotEligible(any(CheerPost.class), anyInt(), any(Instant.class))).thenReturn(false);
        when(followService.getFollowingIds(anyLong())).thenReturn(Collections.emptyList());
        when(blockService.getBlockedIds(anyLong())).thenReturn(Collections.emptyList());
        when(blockService.getBlockerIds(anyLong())).thenReturn(Collections.emptyList());

        PermissionValidator permissionValidator = new PermissionValidator();
        PublicVisibilityVerifier publicVisibilityVerifier = new PublicVisibilityVerifier(userBlockRepository,
                userFollowRepository);
        HotPostChecker hotPostChecker = new HotPostChecker(scoringService);
        PostDtoMapper postDtoMapper = new PostDtoMapper(hotPostChecker, imageService, redisPostService,
                profileImageService);
        CheerInteractionService interactionService = new CheerInteractionService(
                likeRepo,
                bookmarkRepo,
                commentLikeRepo,
                reportRepo,
                postRepo,
                commentRepo,
                repostRepo,
                userRepository,
                notificationService,
                blockService,
                publicVisibilityVerifier,
                permissionValidator,
                entityManager,
                postService);
        CheerLinkedPostService linkedPostService = new CheerLinkedPostService(
                diaryRepository, partyRepository, postRepo);

        feedService = new CheerFeedService(
                postRepo,
                interactionService,
                imageService,
                redisPostService,
                scoringService,
                followService,
                blockService,
                publicVisibilityVerifier,
                userService,
                permissionValidator,
                postDtoMapper,
                profileImageService,
                bookmarkRepo,
                new CheerMonitoringMetricsService(new SimpleMeterRegistry()),
                linkedPostService);

        // @DataJpaTest는 테스트 트랜잭션 내 미커밋 데이터를 사용한다.
        // 병렬 virtual thread는 별도 트랜잭션이므로 미커밋 데이터를 볼 수 없다.
        // 호출 스레드에서 동기 실행하는 executor로 교체해 트랜잭션 공유를 유지한다.
        feedService.setFeedEnrichmentExecutorForTest(new AbstractExecutorService() {
            @Override public void execute(Runnable command) { command.run(); }
            @Override public void shutdown() {}
            @Override public List<Runnable> shutdownNow() { return Collections.emptyList(); }
            @Override public boolean isShutdown() { return false; }
            @Override public boolean isTerminated() { return false; }
            @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
        });
    }

    @Test
    @DisplayName("full cheer feed query count stays bounded for authenticated viewer interactions")
    void fullFeed_keepsPrepareStatementCountBoundedForViewerInteractions() {
        FeedFixture fixture = seedFeedFixture();
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = HibernateQueryCountSupport.reset(entityManagerFactory);

        var page = feedService.list(null, null, PageRequest.of(0, 10), fixture.viewer());

        assertThat(page.getContent()).hasSize(6);
        assertThat(page.getContent())
                .extracting("liked")
                .contains(true);
        assertThat(statistics.getPrepareStatementCount())
                .as("observed full cheer feed queries")
                .isLessThanOrEqualTo(FULL_FEED_MAX_QUERIES);
    }

    @Test
    @DisplayName("lightweight cheer feed does not exceed full feed query count")
    void lightweightFeed_doesNotExceedFullFeedQueryCount() {
        FeedFixture fixture = seedFeedFixture();
        entityManager.flush();
        entityManager.clear();

        Statistics fullStatistics = HibernateQueryCountSupport.reset(entityManagerFactory);
        var fullPage = feedService.list(null, null, PageRequest.of(0, 10), fixture.viewer());
        long fullQueryCount = fullStatistics.getPrepareStatementCount();

        entityManager.clear();
        Statistics lightweightStatistics = HibernateQueryCountSupport.reset(entityManagerFactory);
        var lightweightPage = feedService.listLightweight(null, null, PageRequest.of(0, 10), fixture.viewer());
        long lightweightQueryCount = lightweightStatistics.getPrepareStatementCount();

        assertThat(fullPage.getContent()).hasSize(6);
        assertThat(lightweightPage.getContent()).hasSize(6);
        assertThat(lightweightQueryCount)
                .as("observed lightweight cheer feed queries")
                .isLessThanOrEqualTo(LIGHTWEIGHT_FEED_MAX_QUERIES);
        assertThat(lightweightQueryCount).isLessThanOrEqualTo(fullQueryCount);
    }

    @Test
    @DisplayName("linked feed query count stays bounded from one source to many mixed sources")
    void linkedFeed_manySourcesAddAtMostTwoBulkQueries() {
        LinkedFeedFixture fixture = seedLinkedFeedFixture();
        entityManager.flush();
        entityManager.clear();

        PageRequest oneRequest = PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "id"));
        PageRequest manyRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id"));

        Statistics oneStatistics = HibernateQueryCountSupport.reset(entityManagerFactory);
        var oneLinkedPage = feedService.list(null, null, oneRequest, null);
        long oneLinkedQueryCount = oneStatistics.getPrepareStatementCount();

        entityManager.clear();
        Statistics manyStatistics = HibernateQueryCountSupport.reset(entityManagerFactory);
        var manyLinkedPage = feedService.list(null, null, manyRequest, null);
        long manyLinkedQueryCount = manyStatistics.getPrepareStatementCount();
        System.out.printf(
                "linked-query-count one=%d many=%d delta=%d%n",
                oneLinkedQueryCount,
                manyLinkedQueryCount,
                manyLinkedQueryCount - oneLinkedQueryCount);

        assertThat(oneLinkedPage.getContent()).hasSize(1);
        assertThat(oneLinkedPage.getContent().getFirst().id()).isEqualTo(fixture.quotePostId());
        assertThat(oneLinkedPage.getContent().getFirst().originalPost()).isNotNull();
        assertThat(oneLinkedPage.getContent().getFirst().originalPost().linkedContent()).isNotNull();
        int naivePerSourceDelta = fixture.uniqueDiarySourceCount()
                + fixture.uniquePartySourceCount()
                - fixture.onePageUniqueSourceCount();
        assertThat(naivePerSourceDelta)
                .as("naive per-source query delta for expanded linked fixture")
                .isGreaterThan(2);
        assertThat(manyLinkedPage.getContent())
                .filteredOn(post -> post.id().equals(fixture.checkinPostId()))
                .singleElement()
                .extracting("linkedContent.kind")
                .isEqualTo(com.example.cheerboard.dto.LinkedContentKind.CHECKIN);
        assertThat(manyLinkedPage.getContent())
                .filteredOn(post -> post.id().equals(fixture.recruitmentPostId()))
                .singleElement()
                .extracting("linkedContent.kind")
                .isEqualTo(com.example.cheerboard.dto.LinkedContentKind.RECRUITMENT);
        assertThat(manyLinkedQueryCount)
                .as("many linked source queries=%s, one linked source queries=%s",
                        manyLinkedQueryCount, oneLinkedQueryCount)
                .isLessThanOrEqualTo(oneLinkedQueryCount + 2L);
    }

    private FeedFixture seedFeedFixture() {
        TeamEntity team = persistTeam();
        UserEntity viewer = persistUser("viewer@example.test", "viewer", false);
        UserEntity firstAuthor = persistUser("author-one@example.test", "authorone", false);
        UserEntity secondAuthor = persistUser("author-two@example.test", "authortwo", false);

        List<CheerPost> posts = List.of(
                persistPost(firstAuthor, team, "post-1"),
                persistPost(firstAuthor, team, "post-2"),
                persistPost(firstAuthor, team, "post-3"),
                persistPost(secondAuthor, team, "post-4"),
                persistPost(secondAuthor, team, "post-5"),
                persistPost(secondAuthor, team, "post-6"));

        persistLike(posts.get(0), viewer);
        persistLike(posts.get(1), viewer);
        persistBookmark(posts.get(1), viewer);
        persistBookmark(posts.get(2), viewer);
        persistRepost(posts.get(3), viewer);

        return new FeedFixture(viewer);
    }

    private LinkedFeedFixture seedLinkedFeedFixture() {
        TeamEntity team = persistTeam();
        UserEntity author = persistUser("linked-author@example.test", "linkedauthor", false);
        GameEntity firstGame = persistGame("LINKED-QC-1", LocalDate.of(2026, 7, 13));
        GameEntity secondGame = persistGame("LINKED-QC-2", LocalDate.of(2026, 7, 14));
        GameEntity thirdGame = persistGame("LINKED-QC-3", LocalDate.of(2026, 7, 15));
        GameEntity fourthGame = persistGame("LINKED-QC-4", LocalDate.of(2026, 7, 16));
        BegaDiary firstDiary = persistDiary(author, firstGame);
        BegaDiary secondDiary = persistDiary(author, secondGame);
        BegaDiary thirdDiary = persistDiary(author, thirdGame);
        BegaDiary fourthDiary = persistDiary(author, fourthGame);
        Party firstParty = persistParty(author, LocalDate.of(2026, 7, 17), "Blue");
        Party secondParty = persistParty(author, LocalDate.of(2026, 7, 18), "Red");
        Party thirdParty = persistParty(author, LocalDate.of(2026, 7, 19), "Green");
        Party fourthParty = persistParty(author, LocalDate.of(2026, 7, 20), "Navy");

        CheerPost checkin = persistLinkedPost(
                author, team, "checkin", PostType.CHECKIN, firstDiary.getId(), null, null);
        CheerPost recruitment = persistLinkedPost(
                author, team, "recruitment", PostType.RECRUITMENT, null, firstParty.getId(), null);
        persistLinkedPost(author, team, "second-checkin", PostType.CHECKIN, secondDiary.getId(), null, null);
        persistLinkedPost(author, team, "third-checkin", PostType.CHECKIN, thirdDiary.getId(), null, null);
        persistLinkedPost(author, team, "fourth-checkin", PostType.CHECKIN, fourthDiary.getId(), null, null);
        persistLinkedPost(author, team, "second-recruitment", PostType.RECRUITMENT, null, secondParty.getId(), null);
        persistLinkedPost(author, team, "third-recruitment", PostType.RECRUITMENT, null, thirdParty.getId(), null);
        persistLinkedPost(author, team, "fourth-recruitment", PostType.RECRUITMENT, null, fourthParty.getId(), null);
        CheerPost quote = persistLinkedPost(
                author, team, "quote", PostType.NORMAL, null, null, checkin);
        quote.setRepostType(CheerPost.RepostType.QUOTE);

        return new LinkedFeedFixture(checkin.getId(), recruitment.getId(), quote.getId(), 4, 4, 1);
    }

    private GameEntity persistGame(String gameId, LocalDate gameDate) {
        GameEntity game = GameEntity.builder()
                .gameId(gameId)
                .gameDate(gameDate)
                .stadium("Jamsil")
                .homeTeam("LG")
                .awayTeam("KT")
                .seasonId(2026)
                .gameStatus("COMPLETED")
                .isDummy(false)
                .build();
        entityManager.persist(game);
        return game;
    }

    private BegaDiary persistDiary(UserEntity owner, GameEntity game) {
        BegaDiary diary = BegaDiary.builder()
                .diaryDate(game.getGameDate())
                .game(game)
                .memo("private fixture memo")
                .mood(BegaDiary.DiaryEmoji.HAPPY)
                .type(BegaDiary.DiaryType.ATTENDED)
                .winning(BegaDiary.DiaryWinning.WIN)
                .photoUrls(List.of())
                .user(owner)
                .team("LG")
                .stadium(game.getStadium())
                .ticketVerified(true)
                .ticketVerifiedAt(LocalDateTime.now())
                .build();
        entityManager.persist(diary);
        return diary;
    }

    private Party persistParty(UserEntity host, LocalDate gameDate, String section) {
        Party party = Party.builder()
                .hostId(host.getId())
                .hostName(host.getName())
                .hostBadge(Party.BadgeType.VERIFIED)
                .teamId("LG")
                .gameDate(gameDate)
                .gameTime(LocalTime.of(18, 30))
                .stadium("Jamsil")
                .homeTeam("LG")
                .awayTeam("KIA")
                .section(section)
                .maxParticipants(4)
                .currentParticipants(1)
                .description("query count fixture")
                .ticketVerified(true)
                .status(Party.PartyStatus.PENDING)
                .build();
        entityManager.persist(party);
        return party;
    }

    private CheerPost persistLinkedPost(
            UserEntity author,
            TeamEntity team,
            String content,
            PostType postType,
            Long diaryId,
            Long partyId,
            CheerPost original) {
        CheerPost post = CheerPost.builder()
                .team(team)
                .postType(postType)
                .author(author)
                .content(content)
                .diaryId(diaryId)
                .partyId(partyId)
                .repostOf(original)
                .likeCount(0)
                .commentCount(0)
                .views(0)
                .repostCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .deleted(false)
                .build();
        entityManager.persist(post);
        return post;
    }

    private TeamEntity persistTeam() {
        TeamEntity team = TeamEntity.builder()
                .teamId("LG")
                .teamName("LG Twins")
                .teamShortName("LG")
                .city("Seoul")
                .stadiumName("Jamsil")
                .color("#c30452")
                .build();
        entityManager.persist(team);
        return team;
    }

    private UserEntity persistUser(String email, String handle, boolean privateAccount) {
        UserEntity user = UserEntity.builder()
                .uniqueId(UUID.randomUUID())
                .handle(handle)
                .name(handle)
                .email(email)
                .password("encoded-password")
                .role("ROLE_USER")
                .provider("LOCAL")
                .privateAccount(privateAccount)
                .build();
        entityManager.persist(user);
        return user;
    }

    private CheerPost persistPost(UserEntity author, TeamEntity team, String content) {
        CheerPost post = CheerPost.builder()
                .team(team)
                .postType(PostType.NORMAL)
                .author(author)
                .content(content)
                .likeCount(0)
                .commentCount(0)
                .views(0)
                .repostCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .deleted(false)
                .build();
        entityManager.persist(post);
        return post;
    }

    private void persistLike(CheerPost post, UserEntity user) {
        CheerPostLike like = new CheerPostLike();
        like.setId(new CheerPostLike.Id(post.getId(), user.getId()));
        like.setPost(post);
        like.setUser(user);
        entityManager.persist(like);
    }

    private void persistBookmark(CheerPost post, UserEntity user) {
        CheerPostBookmark bookmark = new CheerPostBookmark();
        bookmark.setId(new CheerPostBookmark.Id(post.getId(), user.getId()));
        bookmark.setPost(post);
        bookmark.setUser(user);
        entityManager.persist(bookmark);
    }

    private void persistRepost(CheerPost post, UserEntity user) {
        CheerPostRepost repost = new CheerPostRepost();
        repost.setId(new CheerPostRepost.Id(post.getId(), user.getId()));
        repost.setPost(post);
        repost.setUser(user);
        repost.setCreatedAt(LocalDateTime.now());
        entityManager.persist(repost);
    }

    private record FeedFixture(UserEntity viewer) {
    }

    private record LinkedFeedFixture(
            Long checkinPostId,
            Long recruitmentPostId,
            Long quotePostId,
            int uniqueDiarySourceCount,
            int uniquePartySourceCount,
            int onePageUniqueSourceCount) {
    }
}
