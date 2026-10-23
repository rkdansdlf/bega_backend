package com.example.cheerboard.integration;

import com.example.BegaDiary.Entity.BegaDiary;
import com.example.BegaDiary.Repository.BegaDiaryRepository;
import com.example.admin.entity.AuditLog;
import com.example.admin.repository.AuditLogRepository;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.CheerPostRepost;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.storage.repository.PostImageRepository;
import com.example.cheerboard.storage.strategy.StorageStrategy;
import com.example.cheerboard.storage.strategy.StorageOperationException;
import com.example.media.entity.MediaDomain;
import com.example.media.entity.MediaAsset;
import com.example.media.entity.MediaAssetLink;
import com.example.media.entity.MediaAssetStatus;
import com.example.media.entity.MediaLinkRole;
import com.example.media.repository.MediaAssetLinkRepository;
import com.example.media.repository.MediaAssetRepository;
import com.example.media.service.MediaLinkService;
import com.example.cheerboard.dto.CreateCommentReq;
import com.example.cheerboard.dto.CreatePostReq;
import com.example.cheerboard.dto.RepostToggleResponse;
import com.example.cheerboard.service.CheerPostCreationResult;
import com.example.cheerboard.entity.CheerVoteEntity;
import com.example.cheerboard.entity.CheerVoteId;
import com.example.cheerboard.exception.DuplicateCommentException;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerPostRepostRepo;
import com.example.cheerboard.repository.CheerVoteRepository;
import com.example.cheerboard.repository.CheerBattleLogRepository;
import com.example.cheerboard.service.CheerBattleService;
import com.example.cheerboard.service.CheerCommentService;
import com.example.cheerboard.service.CheerInteractionService;
import com.example.cheerboard.service.CheerPostService;
import com.example.cheerboard.service.CheerPostTransactionBoundary;
import com.example.cheerboard.storage.service.ImageService;
import com.example.cheerboard.service.CheerService;
import com.example.kbo.entity.GameEntity;
import com.example.kbo.entity.TeamEntity;
import com.example.kbo.repository.GameRepository;
import com.example.kbo.repository.TeamRepository;
import com.example.common.exception.NotFoundBusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyString;

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

    @MockitoSpyBean
    private CheerPostRepo postRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private CheerBattleLogRepository cheerBattleLogRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @MockitoSpyBean
    private CheerPostRepostRepo repostRepo;

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

    @MockitoSpyBean
    private PostImageRepository postImageRepository;

    @Autowired
    private MediaAssetRepository mediaAssetRepository;

    @Autowired
    private MediaAssetLinkRepository mediaAssetLinkRepository;

    @Autowired
    private ImageService imageService;

    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean
    private StorageStrategy storageStrategy;

    @MockitoSpyBean
    private MediaLinkService mediaLinkService;

    @MockitoSpyBean
    private CheerPostTransactionBoundary postTransactionBoundary;

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean(name = "cacheManager")
    private CacheManager cacheManager;

    private final CacheManager testCacheManager = new ConcurrentMapCacheManager();

    private UserEntity author;
    private TeamEntity team;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> testCacheManager.getCache(invocation.getArgument(0)))
                .when(cacheManager).getCache(anyString());
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

        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_cheer_post_active_diary ON cheer_post(diary_id)");
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
    @DisplayName("Duplicate repost recovery runs after the failed transaction has rolled back")
    void toggleRepost_recoversUniqueConflictOutsideFailedTransaction() {
        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_test_simple_repost "
                + "ON cheer_post(author_id, repost_of_id, repost_type)");
        CheerPost original = postRepo.saveAndFlush(CheerPost.builder()
                .author(author)
                .team(team)
                .content("Original Content")
                .postType(PostType.NORMAL)
                .shareMode(CheerPost.ShareMode.INTERNAL_REPOST)
                .repostCount(1)
                .build());
        UserEntity reposter = userRepo.saveAndFlush(UserEntity.builder()
                .email("same-reposter+" + UUID.randomUUID() + "@test.com")
                .name("Reposter")
                .provider("LOCAL")
                .handle("sr" + UUID.randomUUID().toString().substring(0, 6))
                .uniqueId(UUID.randomUUID())
                .role("ROLE_USER")
                .build());
        postRepo.saveAndFlush(CheerPost.builder()
                .author(reposter)
                .team(team)
                .repostOf(original)
                .repostType(CheerPost.RepostType.SIMPLE)
                .shareMode(CheerPost.ShareMode.INTERNAL_REPOST)
                .content("")
                .postType(PostType.NORMAL)
                .build());
        CheerPostRepost winningTracking = new CheerPostRepost();
        winningTracking.setId(new CheerPostRepost.Id(original.getId(), reposter.getId()));
        winningTracking.setPost(original);
        winningTracking.setUser(reposter);
        repostRepo.saveAndFlush(winningTracking);

        AtomicBoolean fallbackReadOccurred = new AtomicBoolean();
        AtomicBoolean fallbackUsedRollbackOnlyTransaction = new AtomicBoolean();
        doReturn(java.util.Optional.empty()).when(postRepo).findByAuthorAndRepostOfAndRepostType(
                any(UserEntity.class), any(CheerPost.class), eq(CheerPost.RepostType.SIMPLE));
        doReturn(1).when(postRepo).findRepostCountById(original.getId());
        doAnswer(invocation -> {
            fallbackReadOccurred.set(true);
            fallbackUsedRollbackOnlyTransaction.set(
                    TransactionAspectSupport.currentTransactionStatus().isRollbackOnly());
            return true;
        }).when(repostRepo).existsByPostIdAndUserId(original.getId(), reposter.getId());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(reposter.getId(), null, List.of()));
        RepostToggleResponse response;
        try {
            response = cheerService.toggleRepost(original.getId());
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(response.reposted()).isTrue();
        assertThat(response.count()).isEqualTo(1);
        assertThat(fallbackReadOccurred).isTrue();
        assertThat(fallbackUsedRollbackOnlyTransaction).isFalse();
        assertThat(repostRepo.countByPostId(original.getId())).isEqualTo(1L);
        assertThat(postRepo.findById(original.getId()).orElseThrow().getRepostCount()).isEqualTo(1);
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
    @DisplayName("Post deletion waits for an in-flight image upload and deletes its committed image outside a DB transaction")
    void deletePost_waitsForConcurrentImageUploadAndCleansItsCommittedImage() throws Exception {
        CheerPost post = postRepo.saveAndFlush(CheerPost.builder()
                .author(author)
                .team(team)
                .content("Concurrent image upload")
                .postType(PostType.NORMAL)
                .shareMode(CheerPost.ShareMode.INTERNAL_REPOST)
                .build());
        Long postId = post.getId();
        byte[] imageBytes;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", output);
            imageBytes = output.toByteArray();
        }
        MockMultipartFile uploadFile = new MockMultipartFile(
                "files", "concurrent-image.png", "image/png", imageBytes);
        CountDownLatch uploadReachedStorage = new CountDownLatch(1);
        CountDownLatch releaseUploader = new CountDownLatch(1);
        CountDownLatch deletionStarted = new CountDownLatch(1);
        CountDownLatch storageDeleteStarted = new CountDownLatch(1);
        AtomicBoolean storageDeleteHadTransaction = new AtomicBoolean(true);
        AtomicReference<String> uploadedStoragePath = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        doAnswer(invocation -> {
            String storagePath = invocation.getArgument(3);
            uploadedStoragePath.set(storagePath);
            uploadReachedStorage.countDown();
            if (!releaseUploader.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release image uploader");
            }
            return reactor.core.publisher.Mono.just(storagePath);
        }).when(storageStrategy).uploadBytes(any(byte[].class), anyString(), anyString(), anyString());
        doAnswer(invocation -> {
            storageDeleteStarted.countDown();
            storageDeleteHadTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
            return reactor.core.publisher.Mono.empty();
        }).when(storageStrategy).deleteChecked(anyString(), anyString());

        Future<?> upload = executor.submit(() -> {
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(author.getId(), null, List.of()));
            try {
                imageService.uploadPostImages(postId, List.of(uploadFile));
            } finally {
                SecurityContextHolder.clearContext();
                RequestContextHolder.resetRequestAttributes();
            }
        });

        Future<?> deletion = null;
        try {
            assertThat(uploadReachedStorage.await(5, TimeUnit.SECONDS)).isTrue();
            deletion = executor.submit(() -> {
                deletionStarted.countDown();
                postService.deletePostByAdmin(postId, ignored -> {});
            });

            assertThat(deletionStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(storageDeleteStarted.await(500, TimeUnit.MILLISECONDS)).isFalse();
            assertThat(deletion.isDone()).isFalse();

            releaseUploader.countDown();
            upload.get(10, TimeUnit.SECONDS);
            deletion.get(10, TimeUnit.SECONDS);

            verify(storageStrategy).deleteChecked(anyString(), org.mockito.ArgumentMatchers.eq(uploadedStoragePath.get()));
            assertThat(storageDeleteHadTransaction).isFalse();
            assertThat(postImageRepository.countByPostId(postId)).isZero();
            assertThat(postRepo.findById(postId)).isEmpty();
        } finally {
            releaseUploader.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("피드 이미지 signed URL 발급 중 게시글 삭제는 잠금 해제까지 대기하고 이후 캐시 URL도 차단한다")
    void deletePost_waitsForFeedImageUrlIssuanceAndFeedSkipsSoftDeletedPost() throws Exception {
        CheerPost post = postRepo.saveAndFlush(CheerPost.builder()
                .author(author)
                .team(team)
                .content("Concurrent image URL issue")
                .postType(PostType.NORMAL)
                .shareMode(CheerPost.ShareMode.INTERNAL_REPOST)
                .build());
        Long postId = post.getId();
        String storagePath = "posts/" + postId + "/concurrent-image.png";
        postImageRepository.saveAndFlush(com.example.cheerboard.storage.entity.PostImage.builder()
                .post(post)
                .storagePath(storagePath)
                .mimeType("image/png")
                .bytes(1L)
                .isThumbnail(false)
                .build());

        CountDownLatch urlGenerationStarted = new CountDownLatch(1);
        CountDownLatch releaseUrlGeneration = new CountDownLatch(1);
        CountDownLatch deletionStarted = new CountDownLatch(1);
        CountDownLatch deletionLockRequested = new CountDownLatch(1);
        AtomicReference<Thread> deletionThread = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        doAnswer(invocation -> reactor.core.publisher.Mono.fromCallable(() -> {
            urlGenerationStarted.countDown();
            if (!releaseUrlGeneration.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release signed URL generation");
            }
            return "https://signed.example/" + storagePath;
        })).when(storageStrategy).getUrl(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());
        doAnswer(invocation -> reactor.core.publisher.Mono.error(new IllegalStateException("retry storage delete")))
                .when(storageStrategy).deleteChecked(anyString(), anyString());
        doAnswer(invocation -> {
            if (deletionThread.get() == Thread.currentThread()) {
                deletionLockRequested.countDown();
            }
            return invocation.callRealMethod();
        }).when(postTransactionBoundary).inWriteTransaction(any(java.util.function.Supplier.class));

        Future<java.util.Map<Long, List<String>>> urlLookup = executor.submit(
                () -> imageService.getPostImageUrlsByPostIds(List.of(postId)));
        Future<?> deletion = null;

        try {
            boolean generationStarted = urlGenerationStarted.await(5, TimeUnit.SECONDS);
            if (!generationStarted) {
                try {
                    urlLookup.get(5, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    throw new AssertionError("Feed URL lookup failed before signed URL generation", failure);
                }
            }
            assertThat(generationStarted).isTrue();
            deletion = executor.submit(() -> {
                deletionThread.set(Thread.currentThread());
                deletionStarted.countDown();
                postService.deletePostByAdmin(postId, ignored -> {});
            });

            assertThat(deletionStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(deletionLockRequested.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(300);
            assertThat(deletion.isDone()).isFalse();

            releaseUrlGeneration.countDown();
            assertThat(urlLookup.get(10, TimeUnit.SECONDS).get(postId))
                    .containsExactly("https://signed.example/" + storagePath);
            deletion.get(10, TimeUnit.SECONDS);
        } finally {
            releaseUrlGeneration.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertThat(postRepo.findSoftDeletedPosts()).extracting(CheerPost::getId).contains(postId);

        @SuppressWarnings("unchecked")
        ValueOperations<String, String> cachedUrls = (ValueOperations<String, String>) org.mockito.Mockito
                .mock(ValueOperations.class);
        org.mockito.Mockito.doReturn(cachedUrls).when(stringRedisTemplate).opsForValue();
        org.mockito.Mockito.doReturn("[\"https://signed.example/stale\"]")
                .when(cachedUrls).get("postImageUrls::" + postId);
        assertThat(imageService.getPostImageUrlsByPostIds(List.of(postId)).get(postId)).isEmpty();
        verify(storageStrategy, org.mockito.Mockito.times(1))
                .getUrl(anyString(), eq(storagePath), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("개별 이미지 삭제는 피드 URL 발급과 직렬화되고 스토리지 실패 시 재시도 상태를 보존한다")
    void deleteImage_waitsForFeedUrlAndRetainsImageUntilStorageDeleteSucceeds() throws Exception {
        CheerPost post = postRepo.saveAndFlush(CheerPost.builder()
                .author(author)
                .team(team)
                .content("Concurrent individual image deletion")
                .postType(PostType.NORMAL)
                .shareMode(CheerPost.ShareMode.INTERNAL_REPOST)
                .build());
        Long postId = post.getId();
        String storagePath = "posts/" + postId + "/individual-delete.png";
        var image = postImageRepository.saveAndFlush(com.example.cheerboard.storage.entity.PostImage.builder()
                .post(post)
                .storagePath(storagePath)
                .mimeType("image/png")
                .bytes(1L)
                .isThumbnail(false)
                .build());

        CountDownLatch urlGenerationStarted = new CountDownLatch(1);
        CountDownLatch releaseUrlGeneration = new CountDownLatch(1);
        CountDownLatch storageDeleteStarted = new CountDownLatch(1);
        AtomicBoolean failFirstStorageDelete = new AtomicBoolean(true);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Map<String, String> postImageUrlCache = new ConcurrentHashMap<>();
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> postImageUrlValues = (ValueOperations<String, String>) org.mockito.Mockito
                .mock(ValueOperations.class);
        doReturn(postImageUrlValues).when(stringRedisTemplate).opsForValue();
        doAnswer(invocation -> postImageUrlCache.get(invocation.getArgument(0)))
                .when(postImageUrlValues).get(anyString());
        doAnswer(invocation -> {
            postImageUrlCache.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(postImageUrlValues).set(anyString(), anyString(), any(Duration.class));
        doAnswer(invocation -> postImageUrlCache.remove(invocation.getArgument(0)) != null)
                .when(stringRedisTemplate).delete(anyString());

        doAnswer(invocation -> reactor.core.publisher.Mono.fromCallable(() -> {
            urlGenerationStarted.countDown();
            if (!releaseUrlGeneration.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release signed URL generation");
            }
            return "https://signed.example/" + storagePath;
        })).when(storageStrategy).getUrl(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());
        doAnswer(invocation -> {
            storageDeleteStarted.countDown();
            return failFirstStorageDelete.getAndSet(false)
                    ? reactor.core.publisher.Mono.error(new StorageOperationException(
                            StorageOperationException.Kind.TRANSIENT,
                            "temporary storage failure",
                            new IllegalStateException("synthetic failure")))
                    : reactor.core.publisher.Mono.empty();
        }).when(storageStrategy).deleteChecked(anyString(), eq(storagePath));

        Future<Map<Long, List<String>>> urlLookup = executor.submit(
                () -> imageService.getPostImageUrlsByPostIds(List.of(postId)));
        try {
            boolean generationStarted = urlGenerationStarted.await(5, TimeUnit.SECONDS);
            if (!generationStarted) {
                assertThat(postImageRepository.countByPostId(postId)).isEqualTo(1L);
                assertThat(urlLookup.get(5, TimeUnit.SECONDS).get(postId))
                        .as("the URL lookup should issue a signed URL for the persisted image")
                        .containsExactly("https://signed.example/" + storagePath);
            }
            assertThat(generationStarted).isTrue();
            Future<?> deletion = executor.submit(() -> {
                RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(author.getId(), null, List.of()));
                try {
                    imageService.deleteImage(image.getId());
                } finally {
                    SecurityContextHolder.clearContext();
                    RequestContextHolder.resetRequestAttributes();
                }
            });

            assertThat(storageDeleteStarted.await(300, TimeUnit.MILLISECONDS)).isFalse();
            Thread.sleep(300);
            assertThat(deletion.isDone()).isFalse();

            releaseUrlGeneration.countDown();
            assertThat(urlLookup.get(10, TimeUnit.SECONDS).get(postId))
                    .containsExactly("https://signed.example/" + storagePath);
            String postImageCacheKey = "postImageUrls::" + postId;
            assertThat(postImageUrlCache).containsEntry(postImageCacheKey,
                    "[\"https://signed.example/" + storagePath + "\"]");
            assertThat(storageDeleteStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> deletion.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(StorageOperationException.class);

            assertThat(postImageRepository.findById(image.getId())).isPresent();
            assertThat(postImageUrlCache).containsKey(postImageCacheKey);
            assertThat(testCacheManager.getCache("signedUrls").get(storagePath, String.class))
                    .isEqualTo("https://signed.example/" + storagePath);

            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(author.getId(), null, List.of()));
            try {
                imageService.deleteImage(image.getId());
            } finally {
                SecurityContextHolder.clearContext();
                RequestContextHolder.resetRequestAttributes();
            }

            assertThat(postImageRepository.findById(image.getId())).isEmpty();
            assertThat(postImageUrlCache).doesNotContainKey(postImageCacheKey);
            assertThat(testCacheManager.getCache("signedUrls").get(storagePath)).isNull();
        } finally {
            releaseUrlGeneration.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("이미지 삭제가 잠금 경합에서 먼저 끝나면 서명 URL 재발급은 삭제된 이미지를 반환하지 않는다")
    void renewSignedUrl_rechecksImageAfterWaitingForPostLock() throws Exception {
        assertImageOperationRejectsImageDeletedWhileWaitingForPostLock(
                imageService::renewSignedUrl);
    }

    @Test
    @DisplayName("이미지 삭제가 잠금 경합에서 먼저 끝나면 삭제된 이미지를 썸네일로 되살리지 않는다")
    void markAsThumbnail_rechecksImageAfterWaitingForPostLock() throws Exception {
        assertImageOperationRejectsImageDeletedWhileWaitingForPostLock(
                imageService::markAsThumbnail);
    }

    private void assertImageOperationRejectsImageDeletedWhileWaitingForPostLock(
            java.util.function.Function<Long, ?> imageOperation) throws Exception {
        CheerPost post = postRepo.saveAndFlush(CheerPost.builder()
                .author(author)
                .team(team)
                .content("Image operation and deletion race")
                .postType(PostType.NORMAL)
                .shareMode(CheerPost.ShareMode.INTERNAL_REPOST)
                .build());
        Long postId = post.getId();
        String storagePath = "posts/" + postId + "/operation-delete-race.png";
        var image = postImageRepository.saveAndFlush(com.example.cheerboard.storage.entity.PostImage.builder()
                .post(post)
                .storagePath(storagePath)
                .mimeType("image/png")
                .bytes(1L)
                .isThumbnail(false)
                .build());

        CountDownLatch initialImageLoaded = new CountDownLatch(1);
        CountDownLatch releaseInitialImage = new CountDownLatch(1);
        AtomicReference<Thread> operationThread = new AtomicReference<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        doAnswer(invocation -> {
            com.example.cheerboard.storage.entity.PostImage loadedImage = entityManager.find(
                    com.example.cheerboard.storage.entity.PostImage.class,
                    invocation.getArgument(0));
            if (Thread.currentThread() == operationThread.get()) {
                initialImageLoaded.countDown();
                if (!releaseInitialImage.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to release initial image lookup");
                }
            }
            return java.util.Optional.ofNullable(loadedImage);
        }).when(postImageRepository).findById(eq(image.getId()));
        doReturn(reactor.core.publisher.Mono.just("https://signed.example/" + storagePath))
                .when(storageStrategy).getUrl(anyString(), eq(storagePath), org.mockito.ArgumentMatchers.anyInt());
        doReturn(reactor.core.publisher.Mono.empty())
                .when(storageStrategy).deleteChecked(anyString(), eq(storagePath));

        Future<?> operation = executor.submit(() -> {
            operationThread.set(Thread.currentThread());
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(author.getId(), null, List.of()));
            try {
                return imageOperation.apply(image.getId());
            } finally {
                SecurityContextHolder.clearContext();
                RequestContextHolder.resetRequestAttributes();
            }
        });

        try {
            assertThat(initialImageLoaded.await(5, TimeUnit.SECONDS)).isTrue();

            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(author.getId(), null, List.of()));
            try {
                imageService.deleteImage(image.getId());
            } finally {
                SecurityContextHolder.clearContext();
                RequestContextHolder.resetRequestAttributes();
            }

            assertThat(postImageRepository.findById(image.getId())).isEmpty();
            releaseInitialImage.countDown();

            assertThatThrownBy(() -> operation.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(NotFoundBusinessException.class);
            verify(storageStrategy, never())
                    .getUrl(anyString(), eq(storagePath), org.mockito.ArgumentMatchers.anyInt());
            assertThat(postImageRepository.findById(image.getId())).isEmpty();
        } finally {
            releaseInitialImage.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("Admin audit failure rolls back the soft-delete before storage cleanup starts")
    void deletePostByAdmin_auditFailureRollsBackSoftDelete() {
        CheerPost post = postRepo.saveAndFlush(CheerPost.builder()
                .author(author)
                .team(team)
                .content("Audit rollback")
                .postType(PostType.NORMAL)
                .shareMode(CheerPost.ShareMode.INTERNAL_REPOST)
                .build());
        long auditCountBefore = auditLogRepository.count();

        assertThatThrownBy(() -> postService.deletePostByAdmin(post.getId(), ignored -> {
            auditLogRepository.saveAndFlush(AuditLog.builder()
                    .adminId(999L)
                    .targetUserId(author.getId())
                    .action(AuditLog.AuditAction.DELETE_POST)
                    .oldValue("Audit rollback")
                    .description("게시글 삭제 (ID: " + post.getId() + ")")
                    .createdAt(LocalDateTime.now())
                    .build());
            throw new IllegalStateException("audit write failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(postRepo.findById(post.getId())).isPresent();
        assertThat(auditLogRepository.count()).isEqualTo(auditCountBefore);
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
    }

    @Test
    @DisplayName("DB finalization failure retains soft-deleted post and image path for scheduled retry")
    void cleanupSoftDeletedPost_finalizationFailureRetainsRetryState() {
        CheerPost post = postRepo.saveAndFlush(CheerPost.builder()
                .author(author)
                .team(team)
                .content("Retry image cleanup")
                .postType(PostType.NORMAL)
                .shareMode(CheerPost.ShareMode.INTERNAL_REPOST)
                .build());
        String storagePath = "posts/" + post.getId() + "/retry-image.png";
        postImageRepository.saveAndFlush(com.example.cheerboard.storage.entity.PostImage.builder()
                .post(post)
                .storagePath(storagePath)
                .mimeType("image/png")
                .bytes(1L)
                .isThumbnail(false)
                .build());
        LocalDateTime now = LocalDateTime.now();
        MediaAsset asset = mediaAssetRepository.saveAndFlush(MediaAsset.builder()
                .ownerUserId(author.getId())
                .domain(MediaDomain.CHEER)
                .status(MediaAssetStatus.READY)
                .declaredContentType("image/png")
                .declaredBytes(1L)
                .declaredWidth(1)
                .declaredHeight(1)
                .stagingObjectKey("staging/" + post.getId() + "/retry-image.png")
                .objectKey(storagePath)
                .storedContentType("image/png")
                .storedBytes(1L)
                .storedWidth(1)
                .storedHeight(1)
                .uploadExpiresAt(now.plusHours(1))
                .finalizedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build());
        mediaAssetLinkRepository.saveAndFlush(MediaAssetLink.builder()
                .assetId(asset.getId())
                .asset(asset)
                .domain(MediaDomain.CHEER)
                .entityId(post.getId())
                .role(MediaLinkRole.CHEER_IMAGE)
                .build());
        doAnswer(invocation -> reactor.core.publisher.Mono.empty())
                .when(storageStrategy).deleteChecked(anyString(), eq(storagePath));
        doAnswer(invocation -> {
            mediaAssetLinkRepository.deleteByDomainAndEntityId(MediaDomain.CHEER, post.getId());
            throw new IllegalStateException("media unlink failed after deleting links");
        })
                .when(mediaLinkService).unlinkEntity(MediaDomain.CHEER, post.getId());

        postService.deletePostByAdmin(post.getId(), ignored -> {});

        assertThat(postRepo.findSoftDeletedPosts()).extracting(CheerPost::getId).contains(post.getId());
        assertThat(postImageRepository.countByPostId(post.getId())).isEqualTo(1L);
        assertThat(mediaAssetLinkRepository.existsByAssetId(asset.getId())).isTrue();
        verify(storageStrategy).deleteChecked(anyString(), eq(storagePath));
    }

    @Test
    @DisplayName("Soft-deleted posts are hidden by the real Hibernate restriction before image URLs are listed")
    void listPostImages_rejectsSoftDeletedPostThroughHibernateRestriction() {
        CheerPost post = postRepo.saveAndFlush(CheerPost.builder()
                .author(author)
                .team(team)
                .content("Soft-deleted image visibility")
                .postType(PostType.NORMAL)
                .shareMode(CheerPost.ShareMode.INTERNAL_REPOST)
                .build());
        postImageRepository.saveAndFlush(com.example.cheerboard.storage.entity.PostImage.builder()
                .post(post)
                .storagePath("posts/" + post.getId() + "/hidden-image.png")
                .mimeType("image/png")
                .bytes(1L)
                .isThumbnail(false)
                .build());

        post.setDeleted(true);
        postRepo.saveAndFlush(post);
        entityManager.clear();

        assertThat(postRepo.findById(post.getId())).isEmpty();
        assertThatThrownBy(() -> imageService.listPostImages(post.getId()))
                .isInstanceOfSatisfying(NotFoundBusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("CHEER_POST_NOT_FOUND"));
        verify(storageStrategy, never()).getUrl(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());
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

    @Test
    @DisplayName("한 포인트의 동시 다중 경기 투표는 한 건만 승인하고 잔액을 정확히 차감한다")
    void concurrentVotesAcrossGamesCannotSpendOnePointTwice() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserEntity voter = userRepo.saveAndFlush(UserEntity.builder()
                .email("battle-racer+" + suffix + "@test.com")
                .name("Battle Racer")
                .handle("br" + suffix)
                .uniqueId(UUID.randomUUID())
                .provider("LOCAL")
                .role("ROLE_USER")
                .cheerPoints(1)
                .build());
        Long voterId = voter.getId();
        String firstGameId = "battle-a-" + suffix;
        String secondGameId = "battle-b-" + suffix;
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Boolean> firstVote = executor.submit(() -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start first vote");
            }
            return voteIfAllowed(firstGameId, voterId);
        });
        Future<Boolean> secondVote = executor.submit(() -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start second vote");
            }
            return voteIfAllowed(secondGameId, voterId);
        });

        try {
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(
                    firstVote.get(15, TimeUnit.SECONDS),
                    secondVote.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        int firstCount = voteRepository.findVoteCount(firstGameId, "LG") == null
                ? 0
                : voteRepository.findVoteCount(firstGameId, "LG");
        int secondCount = voteRepository.findVoteCount(secondGameId, "LG") == null
                ? 0
                : voteRepository.findVoteCount(secondGameId, "LG");
        assertThat(firstCount + secondCount).isEqualTo(1);
        assertThat(userRepo.findById(voterId).orElseThrow().getCheerPoints()).isZero();
        assertThat((cheerBattleLogRepository.findByGameIdAndUserEmail(firstGameId, voter.getEmail()).isPresent()
                ? 1
                : 0)
                + (cheerBattleLogRepository.findByGameIdAndUserEmail(secondGameId, voter.getEmail()).isPresent()
                        ? 1
                        : 0))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("동일 사용자의 같은 경기 중복 동시 투표는 한 건만 기록한다")
    void concurrentDuplicateVotesForSameGameAreRejected() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserEntity voter = userRepo.saveAndFlush(UserEntity.builder()
                .email("battle-duplicate+" + suffix + "@test.com")
                .name("Battle Duplicate")
                .handle("bd" + suffix)
                .uniqueId(UUID.randomUUID())
                .provider("LOCAL")
                .role("ROLE_USER")
                .cheerPoints(10)
                .build());
        Long voterId = voter.getId();
        String gameId = "battle-duplicate-" + suffix;
        voteRepository.saveAndFlush(CheerVoteEntity.builder()
                .gameId(gameId)
                .teamId("LG")
                .voteCount(0)
                .build());

        String voterEmail = voter.getEmail();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Boolean> firstVote = executor.submit(() -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start first duplicate vote");
            }
            return voteIfAllowed(gameId, voterId);
        });
        Future<Boolean> secondVote = executor.submit(() -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start second duplicate vote");
            }
            return voteIfAllowed(gameId, voterId);
        });

        try {
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(
                    firstVote.get(15, TimeUnit.SECONDS),
                    secondVote.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(voteRepository.findVoteCount(gameId, "LG")).isEqualTo(1);
        assertThat(userRepo.findById(voterId).orElseThrow().getCheerPoints()).isEqualTo(9);
        assertThat(cheerBattleLogRepository.findAll().stream()
                .filter(log -> gameId.equals(log.getGameId()) && voterEmail.equals(log.getUserEmail())))
                .hasSize(1);
    }

    private boolean voteIfAllowed(String gameId, Long voterId) {
        try {
            battleService.vote(gameId, "LG", voterId);
            return true;
        } catch (IllegalStateException exception) {
            if ("응원 포인트가 부족합니다.".equals(exception.getMessage())
                    || "이미 투표에 참여하셨습니다.".equals(exception.getMessage())) {
                return false;
            }
            throw exception;
        }
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
