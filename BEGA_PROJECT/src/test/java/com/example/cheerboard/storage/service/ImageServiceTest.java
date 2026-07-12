package com.example.cheerboard.storage.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.service.PublicVisibilityVerifier;
import com.example.cheerboard.config.CurrentUser;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.service.PermissionValidator;
import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.entity.PostImage;
import com.example.cheerboard.storage.repository.PostImageRepository;
import com.example.cheerboard.storage.strategy.StorageStrategy;
import com.example.cheerboard.storage.validator.ImageValidator;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.NotFoundBusinessException;
import com.example.media.service.MediaObjectKeyGuard;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    @InjectMocks
    private ImageService imageService;

    @Mock
    private PostImageRepository postImageRepo;

    @Mock
    private CheerPostRepo postRepo;

    @Mock
    private StorageStrategy storageStrategy;

    @Mock
    private ImageValidator validator;

    @Mock
    private StorageConfig config;

    @Mock
    private CurrentUser currentUser;

    @Spy
    private PermissionValidator permissionValidator = new PermissionValidator();

    @Mock
    private CacheManager cacheManager;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> stringValueOperations;

    @Mock
    private com.example.common.image.ImageUtil imageUtil;

    @Mock
    private com.example.common.image.ImageOptimizationMetricsService metricsService;

    @Mock
    private MediaObjectKeyGuard mediaObjectKeyGuard;

    @Mock
    private PublicVisibilityVerifier publicVisibilityVerifier;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOperations);
    }

    @Test
    @DisplayName("I-04: 다른 게시글 소유자의 이미지 삭제 시도는 차단된다")
    void deleteImage_forbidden_for_non_owner() {
        UserEntity postOwner = UserEntity.builder().id(1L).role("ROLE_USER").build();
        UserEntity attacker = UserEntity.builder().id(2L).role("ROLE_USER").build();

        CheerPost post = CheerPost.builder().id(100L).author(postOwner).build();
        PostImage image = PostImage.builder()
                .id(10L)
                .post(post)
                .storagePath("posts/100/demo.webp")
                .mimeType("image/webp")
                .bytes(1024L)
                .isThumbnail(false)
                .build();

        when(currentUser.get()).thenReturn(attacker);
        when(postImageRepo.findById(10L)).thenReturn(Optional.of(image));

        assertThrows(AccessDeniedException.class, () -> imageService.deleteImage(10L));
        verify(postImageRepo, never()).delete(image);
    }

    @Test
    @DisplayName("I-05: 삭제된 게시글의 이미지 목록은 다시 공개되지 않는다")
    void listPostImages_rejectsSoftDeletedPost() {
        when(postRepo.findById(100L)).thenReturn(Optional.empty());

        NotFoundBusinessException ex = assertThrows(NotFoundBusinessException.class,
                () -> imageService.listPostImages(100L));
        org.assertj.core.api.Assertions.assertThat(ex.getCode()).isEqualTo("CHEER_POST_NOT_FOUND");
        verify(postImageRepo, never()).findByPostIdOrderByCreatedAtAsc(100L);
    }

    @Test
    @DisplayName("비공개 또는 차단 게시글 이미지는 legacy 목록 API에서도 signed URL을 만들지 않는다")
    void listPostImages_deniesWhenPostVisibilityRejectsViewer() {
        UserEntity postOwner = UserEntity.builder().id(1L).role("ROLE_USER").build();
        CheerPost post = CheerPost.builder().id(100L).author(postOwner).build();

        when(postRepo.findById(100L)).thenReturn(Optional.of(post));
        doThrow(new AccessDeniedException("blocked")).when(publicVisibilityVerifier)
                .validate(postOwner, 2L, "게시글");

        assertThrows(AccessDeniedException.class, () -> imageService.listPostImages(100L, 2L));
        verify(postImageRepo, never()).findByPostIdOrderByCreatedAtAsc(100L);
        verify(storageStrategy, never()).getUrl(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("I-06: 삭제된 게시글 이미지의 signed URL 재발급은 차단된다")
    void renewSignedUrl_rejectsSoftDeletedPostImage() {
        CheerPost deletedPost = CheerPost.builder().id(100L).build();
        PostImage image = PostImage.builder()
                .id(10L)
                .post(deletedPost)
                .storagePath("posts/100/demo.webp")
                .mimeType("image/webp")
                .bytes(1024L)
                .isThumbnail(false)
                .build();

        when(postImageRepo.findById(10L)).thenReturn(Optional.of(image));
        when(postRepo.findById(100L)).thenReturn(Optional.empty());

        NotFoundBusinessException ex = assertThrows(NotFoundBusinessException.class,
                () -> imageService.renewSignedUrl(10L));
        org.assertj.core.api.Assertions.assertThat(ex.getCode()).isEqualTo("CHEER_POST_NOT_FOUND");
        verify(storageStrategy, never()).getUrl(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("I-07: 다른 게시글 소유자의 이미지 signed URL 재발급은 차단된다")
    void renewSignedUrl_forbidden_for_non_owner() {
        UserEntity postOwner = UserEntity.builder().id(1L).role("ROLE_USER").build();
        UserEntity attacker = UserEntity.builder().id(2L).role("ROLE_USER").build();

        CheerPost post = CheerPost.builder().id(100L).author(postOwner).build();
        PostImage image = PostImage.builder()
                .id(10L)
                .post(post)
                .storagePath("posts/100/demo.webp")
                .mimeType("image/webp")
                .bytes(1024L)
                .isThumbnail(false)
                .build();

        when(currentUser.get()).thenReturn(attacker);
        when(postImageRepo.findById(10L)).thenReturn(Optional.of(image));
        when(postRepo.findById(100L)).thenReturn(Optional.of(post));

        assertThrows(AccessDeniedException.class, () -> imageService.renewSignedUrl(10L));
        verify(storageStrategy, never()).getUrl(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("I-08: 게시글 이미지 캐시가 손상되어도 DB fallback으로 목록 조회를 계속한다")
    void getPostImageUrlsByPostIds_fallsBackWhenCacheEntryIsCorrupted() {
        when(stringValueOperations.get("postImageUrls::1")).thenThrow(new SerializationException("broken cache payload"));
        when(postImageRepo.findByPostIdInOrderByPostIdAscCreatedAtAsc(Collections.singletonList(1L)))
                .thenReturn(Collections.emptyList());

        var result = imageService.getPostImageUrlsByPostIds(Collections.singletonList(1L));

        org.assertj.core.api.Assertions.assertThat(result).containsKey(1L);
        org.assertj.core.api.Assertions.assertThat(result.get(1L)).isEmpty();
        verify(stringRedisTemplate).delete("postImageUrls::1");
        verify(stringValueOperations).set("postImageUrls::1", "[]", Duration.ofMinutes(50));
    }

    @Test
    @DisplayName("I-09: 게시글 이미지 캐시 저장이 실패해도 목록 응답은 계속된다")
    void getPostImageUrlsByPostIds_ignoresCacheWriteFailure() {
        when(stringValueOperations.get("postImageUrls::1")).thenReturn(null);
        when(postImageRepo.findByPostIdInOrderByPostIdAscCreatedAtAsc(Collections.singletonList(1L)))
                .thenReturn(Collections.emptyList());
        doThrow(new SerializationException("write failed")).when(stringValueOperations)
                .set(eq("postImageUrls::1"), eq("[]"), eq(Duration.ofMinutes(50)));

        var result = imageService.getPostImageUrlsByPostIds(Collections.singletonList(1L));

        org.assertj.core.api.Assertions.assertThat(result).containsKey(1L);
        org.assertj.core.api.Assertions.assertThat(result.get(1L)).isEmpty();
        verify(stringValueOperations).set("postImageUrls::1", "[]", Duration.ofMinutes(50));
        verify(stringRedisTemplate).delete("postImageUrls::1");
    }

    @Test
    @DisplayName("I-10: 단건 이미지 URL 조회는 stale cache payload가 깨져도 DB fallback으로 계속된다")
    void getPostImageUrls_fallsBackWhenCacheEntryIsCorrupted() {
        when(stringValueOperations.get("postImageUrls::1")).thenThrow(new SerializationException("broken cache payload"));
        when(postImageRepo.findByPostIdOrderByCreatedAtAsc(1L)).thenReturn(Collections.emptyList());

        var result = imageService.getPostImageUrls(1L);

        org.assertj.core.api.Assertions.assertThat(result).isEmpty();
        verify(stringRedisTemplate).delete("postImageUrls::1");
        verify(stringValueOperations).set("postImageUrls::1", "[]", Duration.ofMinutes(50));
    }

    @Test
    @DisplayName("I-11: 단건 이미지 URL 조회는 legacy type wrapper JSON payload도 복구해서 읽는다")
    void getPostImageUrls_readsLegacyTypeWrappedCachePayload() {
        when(stringValueOperations.get("postImageUrls::1"))
                .thenReturn("[\"java.util.ArrayList\",[\"https://signed.example/posts/1/demo.webp\"]]");

        var result = imageService.getPostImageUrls(1L);

        org.assertj.core.api.Assertions.assertThat(result)
                .containsExactly("https://signed.example/posts/1/demo.webp");
        verify(postImageRepo, never()).findByPostIdOrderByCreatedAtAsc(1L);
        verify(stringValueOperations, never()).set(eq("postImageUrls::1"), any(), any(Duration.class));
    }

    @Test
    @DisplayName("I-12: 단건 이미지 URL 조회는 빈 JSON 배열 cache payload를 그대로 읽는다")
    void getPostImageUrls_readsEmptyJsonArrayCachePayload() {
        when(stringValueOperations.get("postImageUrls::1")).thenReturn("[]");

        var result = imageService.getPostImageUrls(1L);

        org.assertj.core.api.Assertions.assertThat(result).isEmpty();
        verify(postImageRepo, never()).findByPostIdOrderByCreatedAtAsc(1L);
        verify(stringValueOperations, never()).set(eq("postImageUrls::1"), any(), any(Duration.class));
    }

    @Test
    @DisplayName("managed 다이어리 full URL은 owner prefix가 맞을 때만 저장 경로로 정규화된다")
    void normalizeDiaryStoragePathsForWrite_acceptsOwnedManagedUrl() {
        when(config.getDiaryBucket()).thenReturn("diary-bucket");

        List<String> result = imageService.normalizeDiaryStoragePathsForWrite(
                List.of("https://cdn.example.com/diary-bucket/media/diary/10/asset.webp?sig=abc"),
                10L,
                null,
                false);

        org.assertj.core.api.Assertions.assertThat(result).containsExactly("media/diary/10/asset.webp");
    }

    @Test
    @DisplayName("다른 사용자의 managed 다이어리 키는 저장용 경로로 사용할 수 없다")
    void normalizeDiaryStoragePathsForWrite_rejectsUnownedManagedUrl() {
        when(config.getDiaryBucket()).thenReturn("diary-bucket");
        doThrow(new BadRequestBusinessException("MEDIA_ASSET_NOT_FOUND", "업로드를 다시 완료한 뒤 저장해주세요."))
                .when(mediaObjectKeyGuard)
                .requireDiaryWriteKey("media/diary/11/asset.webp", 10L, null, false);

        BadRequestBusinessException exception = assertThrows(
                BadRequestBusinessException.class,
                () -> imageService.normalizeDiaryStoragePathsForWrite(
                        List.of("https://cdn.example.com/diary-bucket/media/diary/11/asset.webp?sig=abc"),
                        10L,
                        null,
                        false));

        org.assertj.core.api.Assertions.assertThat(exception.getCode()).isEqualTo("MEDIA_ASSET_NOT_FOUND");
    }

    @Test
    @DisplayName("managed 다이어리 다운로드는 READY asset 검증 실패 시 스토리지를 호출하지 않는다")
    void downloadDiaryImageBytesForUser_rejectsManagedAssetWhenLedgerRejects() {
        when(mediaObjectKeyGuard.canReadDiaryKey("media/diary/10/asset.webp", 10L, 100L)).thenReturn(false);

        BadRequestBusinessException exception = assertThrows(
                BadRequestBusinessException.class,
                () -> imageService.downloadDiaryImageBytesForUser("media/diary/10/asset.webp", 10L, 100L));

        org.assertj.core.api.Assertions.assertThat(exception.getCode()).isEqualTo("MEDIA_ASSET_NOT_FOUND");
        verify(storageStrategy, never()).download(any(), any());
    }

    @Test
    @DisplayName("다이어리 이미지 업로드는 common pool 대신 전용 이미지 업로드 executor를 사용한다")
    void uploadDiaryImages_usesImageUploadExecutor() throws Exception {
        TrackingDirectExecutorService executor = new TrackingDirectExecutorService();
        imageService.setImageUploadExecutorForTest(executor);
        MockMultipartFile file = new MockMultipartFile(
                "images",
                "seat.png",
                "image/png",
                "demo".getBytes(StandardCharsets.UTF_8));
        var processed = new com.example.common.image.ImageUtil.ProcessedImage(
                "optimized".getBytes(StandardCharsets.UTF_8),
                "image/webp",
                "webp");

        when(config.getMaxImagesPerDiary()).thenReturn(5);
        when(config.getDiaryBucket()).thenReturn("diary-bucket");
        when(imageUtil.process(file, "diary")).thenReturn(processed);
        when(storageStrategy.uploadBytes(
                eq(processed.getBytes()),
                eq("image/webp"),
                eq("diary-bucket"),
                anyString())).thenReturn(Mono.just("diary/10/100/demo.webp"));

        List<String> result = imageService.uploadDiaryImages(10L, 100L, List.of(file)).block();

        org.assertj.core.api.Assertions.assertThat(result).containsExactly("diary/10/100/demo.webp");
        org.assertj.core.api.Assertions.assertThat(executor.executeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("게시글 이미지 URL 일괄 조회는 common pool 대신 전용 이미지 executor를 사용한다")
    void getPostImageUrlsByPostIds_usesImageUploadExecutorForSignedUrls() {
        TrackingDirectExecutorService executor = new TrackingDirectExecutorService();
        imageService.setImageUploadExecutorForTest(executor);
        CheerPost post1 = CheerPost.builder().id(1L).build();
        CheerPost post2 = CheerPost.builder().id(2L).build();
        PostImage image1 = PostImage.builder()
                .id(11L)
                .post(post1)
                .storagePath("posts/1/a.webp")
                .build();
        PostImage image2 = PostImage.builder()
                .id(12L)
                .post(post1)
                .storagePath("posts/1/b.webp")
                .build();
        PostImage image3 = PostImage.builder()
                .id(21L)
                .post(post2)
                .storagePath("posts/2/a.webp")
                .build();

        when(postImageRepo.findByPostIdInOrderByPostIdAscCreatedAtAsc(List.of(1L, 2L)))
                .thenReturn(List.of(image1, image2, image3));
        when(config.getCheerBucket()).thenReturn("cheer-bucket");
        when(storageStrategy.getUrl(eq("cheer-bucket"), eq("posts/1/a.webp"), anyInt()))
                .thenReturn(Mono.just("signed-1a"));
        when(storageStrategy.getUrl(eq("cheer-bucket"), eq("posts/1/b.webp"), anyInt()))
                .thenReturn(Mono.just("signed-1b"));
        when(storageStrategy.getUrl(eq("cheer-bucket"), eq("posts/2/a.webp"), anyInt()))
                .thenReturn(Mono.just("signed-2a"));

        Map<Long, List<String>> result = imageService.getPostImageUrlsByPostIds(List.of(1L, 2L));

        org.assertj.core.api.Assertions.assertThat(result.get(1L)).containsExactly("signed-1a", "signed-1b");
        org.assertj.core.api.Assertions.assertThat(result.get(2L)).containsExactly("signed-2a");
        org.assertj.core.api.Assertions.assertThat(executor.executeCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("이미지 전용 executor는 active/queue gauge를 등록한다")
    void imageUploadExecutorRegistersGauges() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        imageService.setImageUploadMeterRegistryForTest(meterRegistry);
        MockMultipartFile file = new MockMultipartFile(
                "images",
                "seat.png",
                "image/png",
                "demo".getBytes(StandardCharsets.UTF_8));
        var processed = new com.example.common.image.ImageUtil.ProcessedImage(
                "optimized".getBytes(StandardCharsets.UTF_8),
                "image/webp",
                "webp");

        when(config.getMaxImagesPerDiary()).thenReturn(5);
        when(config.getDiaryBucket()).thenReturn("diary-bucket");
        when(imageUtil.process(file, "diary")).thenReturn(processed);
        when(storageStrategy.uploadBytes(
                eq(processed.getBytes()),
                eq("image/webp"),
                eq("diary-bucket"),
                anyString())).thenReturn(Mono.just("diary/10/100/demo.webp"));

        try {
            List<String> result = imageService.uploadDiaryImages(10L, 100L, List.of(file)).block();

            org.assertj.core.api.Assertions.assertThat(result).containsExactly("diary/10/100/demo.webp");
            org.assertj.core.api.Assertions.assertThat(meterRegistry.get("image_upload_executor_active")
                    .tag("executor", "image_upload")
                    .gauge()
                    .value()).isGreaterThanOrEqualTo(0);
            org.assertj.core.api.Assertions.assertThat(meterRegistry.get("image_upload_executor_limit")
                    .tag("executor", "image_upload")
                    .gauge()
                    .value()).isGreaterThanOrEqualTo(2);
            org.assertj.core.api.Assertions.assertThat(meterRegistry.get("image_upload_executor_queued")
                    .tag("executor", "image_upload")
                    .gauge()
                    .value()).isEqualTo(0);
            org.assertj.core.api.Assertions.assertThat(meterRegistry.get("image_upload_executor_queue_capacity")
                    .tag("executor", "image_upload")
                    .gauge()
                    .value()).isEqualTo(64);
        } finally {
            imageService.shutdownImageUploadExecutor();
        }
    }

    private static final class TrackingDirectExecutorService extends AbstractExecutorService {

        private final AtomicInteger executeCount = new AtomicInteger();
        private volatile boolean shutdown;

        int executeCount() {
            return executeCount.get();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(Runnable command) {
            executeCount.incrementAndGet();
            command.run();
        }
    }
}
