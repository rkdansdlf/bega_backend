package com.example.cheerboard.integration;

import com.example.admin.service.AdminService;
import com.example.admin.dto.AdminPostDto;
import com.example.admin.dto.AdminUserDto;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.common.config.CacheConfig;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.CheerPostLike;
import com.example.cheerboard.domain.CheerPostReport;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.domain.ReportReason;
import com.example.cheerboard.dto.LikeToggleResponse;
import com.example.cheerboard.repo.CheerPostLikeRepo;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.cheerboard.service.CheerInteractionService;
import com.example.kbo.entity.TeamEntity;
import com.example.kbo.repository.TeamRepository;
import com.example.support.HibernateQueryCountSupport;
import com.example.support.HibernateStatisticsTestConfig;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(HibernateStatisticsTestConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:cheer_like_reconcile;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=30000",
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
@Transactional
class CheerLikeCountReconciliationIntegrationTest {

    @Autowired
    private CheerInteractionService interactionService;

    @Autowired
    private AdminService adminService;

    @Autowired
    private CheerPostRepo postRepo;

    @Autowired
    private CheerPostLikeRepo likeRepo;

    @Autowired
    private CheerReportRepo reportRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private TeamRepository teamRepo;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    @Qualifier("caffeineCacheManager")
    private CaffeineCacheManager caffeineCacheManager;

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    private TeamEntity team;
    private UserEntity author;
    private UserEntity liker;

    @BeforeEach
    void setUp() {
        caffeineCacheManager.registerCustomCache(
                CacheConfig.RANKING_SHARE_IDS,
                Caffeine.newBuilder().maximumSize(100).build());
        if (!teamRepo.existsById("LG")) {
            team = teamRepo.save(TeamEntity.builder()
                    .teamId("LG")
                    .teamName("LG Twins")
                    .teamShortName("LG")
                    .city("Seoul")
                    .build());
        } else {
            team = teamRepo.findById("LG").orElseThrow();
        }

        author = createUser("Author", "author", "author");
        liker = createUser("Liker", "liker", "liker");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("toggleLike likes a drifted post by exact row recount")
    void toggleLike_reconcilesDriftedLikeCountOnLike() {
        CheerPost post = createPost(author, 6);

        authenticate(author);

        LikeToggleResponse response = interactionService.toggleLike(post.getId(), author);
        clearPersistenceContext();

        assertThat(response.liked()).isTrue();
        assertThat(response.likes()).isEqualTo(1);
        assertThat(likeRepo.countByPostId(post.getId())).isEqualTo(1L);
        assertThat(postRepo.findLikeCountById(post.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("toggleLike unlikes a drifted post by exact row recount")
    void toggleLike_reconcilesDriftedLikeCountOnUnlike() {
        CheerPost post = createPost(author, 5);
        saveLike(post, author);

        authenticate(author);

        LikeToggleResponse response = interactionService.toggleLike(post.getId(), author);
        clearPersistenceContext();

        assertThat(response.liked()).isFalse();
        assertThat(response.likes()).isEqualTo(0);
        assertThat(likeRepo.countByPostId(post.getId())).isEqualTo(0L);
        assertThat(postRepo.findLikeCountById(post.getId())).isEqualTo(0);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("deleteUser reconciles likecount for posts liked by the deleted user")
    void deleteUser_reconcilesAffectedPostLikeCounts() {
        CheerPost post = createPost(author, 4);
        saveLike(post, liker);

        adminService.deleteUser(liker.getId(), null);

        assertThat(likeRepo.countByPostId(post.getId())).isEqualTo(0L);
        assertThat(postRepo.findLikeCountById(post.getId())).isEqualTo(0);
        assertThat(userRepo.findById(liker.getId())).get().extracting(UserEntity::isEnabled).isEqualTo(false);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("admin post listing fetches lazy author and team before DTO mapping")
    void getAdminPosts_fetchesAuthorAndTeam() {
        createPost(author, 0);

        List<AdminPostDto> posts = adminService.getPosts();

        assertThat(posts).singleElement().satisfies(post -> {
            assertThat(post.getAuthor()).isEqualTo(author.getName());
            assertThat(post.getTeam()).isEqualTo(team.getTeamId());
        });
    }

    @Test
    @DisplayName("admin user listing loads all post counts with one aggregate query")
    void getAdminUsers_aggregatesPostCountsInOneQuery() {
        UserEntity firstAuthor = createUser("bug231 first", "b231a", "bug231first");
        UserEntity secondAuthor = createUser("bug231 second", "b231b", "bug231second");
        UserEntity noPostAuthor = createUser("bug231 empty", "b231z", "bug231empty");
        createPost(firstAuthor, 0);
        createPost(firstAuthor, 0);
        createPost(secondAuthor, 0);
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = HibernateQueryCountSupport.reset(entityManagerFactory);

        List<AdminUserDto> users = adminService.getUsers("bug231");

        assertThat(users).hasSize(3);
        Map<Long, Long> postCounts = users.stream().collect(Collectors.toMap(
                AdminUserDto::getId,
                AdminUserDto::getPostCount));
        assertThat(postCounts)
                .containsEntry(firstAuthor.getId(), 2L)
                .containsEntry(secondAuthor.getId(), 1L)
                .containsEntry(noPostAuthor.getId(), 0L);
        assertThat(statistics.getPrepareStatementCount())
                .as("admin user listing and batched post counts")
                .isEqualTo(2);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("deleteUser preserves all authored content while any moderation report remains open")
    void deleteUser_preservesAllAuthoredContentWhenModerationIsOpen() {
        String closedPostContent = "closed-case-private-content";
        String openPostContent = "open-case-preserved-evidence";
        CheerPost closedPost = createSoftDeletedPost(liker, closedPostContent);
        CheerPost openPost = createSoftDeletedPost(liker, openPostContent);
        reportRepo.saveAndFlush(CheerPostReport.builder()
                .post(openPost)
                .reporter(author)
                .reason(ReportReason.SPAM)
                .description("synthetic unresolved report")
                .status(CheerPostReport.ReportStatus.PENDING)
                .build());

        adminService.deleteUser(liker.getId(), null);

        Object[] closedPostRow = loadRawPostFields(closedPost.getId());
        Object[] openPostRow = loadRawPostFields(openPost.getId());
        assertThat(closedPostRow[0]).isEqualTo(closedPostContent);
        assertThat(closedPostRow[1]).isEqualTo("synthetic-source-title");
        assertThat(openPostRow[0]).isEqualTo(openPostContent);
        assertThat(openPostRow[1]).isEqualTo("synthetic-source-title");
    }

    private CheerPost createPost(UserEntity postAuthor, int storedLikeCount) {
        return postRepo.saveAndFlush(CheerPost.builder()
                .author(postAuthor)
                .team(team)
                .content("count reconcile target")
                .postType(PostType.NORMAL)
                .shareMode(CheerPost.ShareMode.INTERNAL_REPOST)
                .likeCount(storedLikeCount)
                .build());
    }

    private void saveLike(CheerPost post, UserEntity user) {
        CheerPostLike like = new CheerPostLike();
        like.setId(new CheerPostLike.Id(post.getId(), user.getId()));
        like.setPost(post);
        like.setUser(user);
        likeRepo.saveAndFlush(like);
    }

    private CheerPost createSoftDeletedPost(UserEntity postAuthor, String content) {
        CheerPost post = createPost(postAuthor, 0);
        post.setContent(content);
        post.setSourceTitle("synthetic-source-title");
        post.setDeleted(true);
        return postRepo.saveAndFlush(post);
    }

    private Object[] loadRawPostFields(Long postId) {
        return (Object[]) entityManager.createNativeQuery(
                        "SELECT CAST(content AS VARCHAR(4000)), CAST(source_title AS VARCHAR(1000)) "
                                + "FROM cheer_post WHERE id = :postId")
                .setParameter("postId", postId)
                .getSingleResult();
    }

    private UserEntity createUser(String name, String handlePrefix, String emailPrefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return userRepo.save(UserEntity.builder()
                .email(emailPrefix + "+" + suffix + "@test.com")
                .name(name)
                .handle(handlePrefix + suffix)
                .uniqueId(UUID.randomUUID())
                .provider("LOCAL")
                .role("ROLE_USER")
                .build());
    }

    private void authenticate(UserEntity user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getId(), null, List.of()));
    }

    private void clearPersistenceContext() {
        entityManager.flush();
        entityManager.clear();
    }
}
