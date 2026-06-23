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
import com.example.cheerboard.service.CheerPostService;
import com.example.cheerboard.service.HotPostChecker;
import com.example.cheerboard.service.PermissionValidator;
import com.example.cheerboard.service.PopularFeedScoringService;
import com.example.cheerboard.service.PostDtoMapper;
import com.example.cheerboard.service.RedisPostService;
import com.example.cheerboard.storage.service.ImageService;
import com.example.kbo.entity.TeamEntity;
import com.example.notification.service.NotificationService;
import com.example.profile.storage.service.ProfileImageService;
import com.example.support.HibernateQueryCountSupport;
import com.example.support.HibernateStatisticsTestConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
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
        when(redisPostService.getCachedHotStatuses(anyCollection())).thenReturn(Collections.emptyMap());
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
                bookmarkRepo);
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
}
