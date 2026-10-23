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
import com.example.cheerboard.storage.strategy.StorageOperationException;
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
import org.mockito.InOrder;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
import static org.mockito.Mockito.inOrder;
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

    @Mock
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(stringValueOperations);
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(invocation -> new SimpleTransactionStatus());
    }

    @Test
    @DisplayName("게시글 이미지 수를 세기 전에 게시글 write-lock 조회를 먼저 사용한다")
    void uploadPostImages_acquiresPostWriteLockBeforeCounting() {
        UserEntity postOwner = UserEntity.builder().id(1L).role("ROLE_USER").build();
        CheerPost post = CheerPost.builder().id(100L).author(postOwner).build();

        when(currentUser.get()).thenReturn(postOwner);
        when(postRepo.findByIdForWrite(100L)).thenReturn(Optional.of(post));
        when(postImageRepo.countByPostId(100L)).thenReturn(0L);

        var result = imageService.uploadPostImages(100L, List.of());

        org.assertj.core.api.Assertions.assertThat(result).isEmpty();
        InOrder writeOrder = inOrder(postRepo, postImageRepo);
        writeOrder.verify(postRepo).findByIdForWrite(100L);
        writeOrder.verify(postImageRepo).countByPostId(100L);
        verify(postRepo, never()).findById(100L);
        verify(postImageRepo, never()).save(any(PostImage.class));
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
    @DisplayName("개별 이미지 삭제가 실패하면 DB 레코드와 URL 캐시 무효화 기회를 보존한다")
    void deleteImage_preservesRetryStateWhenStorageDeleteFails() {
        UserEntity owner = UserEntity.builder().id(1L).role("ROLE_USER").build();
        CheerPost post = CheerPost.builder().id(100L).author(owner).build();
        PostImage image = PostImage.builder()
                .id(10L)
                .post(post)
                .storagePath("posts/100/demo.webp")
                .mimeType("image/webp")
                .bytes(1024L)
                .isThumbnail(false)
                .build();
        when(currentUser.get()).thenReturn(owner);
        when(postImageRepo.findById(10L)).thenReturn(Optional.of(image));
        when(postRepo.findByIdForWrite(100L)).thenReturn(Optional.of(post));
        when(postImageRepo.findByIdAndPostId(10L, 100L)).thenReturn(Optional.of(image));
        when(config.getCheerBucket()).thenReturn("cheer");
        when(storageStrategy.deleteChecked("cheer", "posts/100/demo.webp"))
                .thenReturn(Mono.error(new StorageOperationException(
                        StorageOperationException.Kind.TRANSIENT,
                        "temporary storage failure",
                        new IllegalStateException("synthetic failure"))));

        assertThrows(StorageOperationException.class, () -> imageService.deleteImage(10L));

        verify(postRepo).findByIdForWrite(100L);
        verify(postImageRepo).findByIdAndPostId(10L, 100L);
        verify(storageStrategy).deleteChecked("cheer", "posts/100/demo.webp");
        verify(postImageRepo, never()).delete(image);
        verify(cacheManager, never()).getCache(anyString());
    }

    @Test
    @DisplayName("개별 이미지 삭제는 게시글 잠금 후 스토리지 삭제 성공 시에만 DB와 URL 캐시를 정리한다")
    void deleteImage_locksPostAndDeletesStorageBeforeDatabaseRow() {
        UserEntity owner = UserEntity.builder().id(1L).role("ROLE_USER").build();
        CheerPost post = CheerPost.builder().id(100L).author(owner).build();
        PostImage image = PostImage.builder()
                .id(10L)
                .post(post)
                .storagePath("posts/100/demo.webp")
                .mimeType("image/webp")
                .bytes(1024L)
                .isThumbnail(false)
                .build();
        when(currentUser.get()).thenReturn(owner);
        when(postImageRepo.findById(10L)).thenReturn(Optional.of(image));
        when(postRepo.findByIdForWrite(100L)).thenReturn(Optional.of(post));
        when(postImageRepo.findByIdAndPostId(10L, 100L)).thenReturn(Optional.of(image));
        when(config.getCheerBucket()).thenReturn("cheer");
        when(storageStrategy.deleteChecked("cheer", "posts/100/demo.webp")).thenReturn(Mono.empty());

        imageService.deleteImage(10L);

        InOrder deletionOrder = inOrder(postRepo, postImageRepo, storageStrategy);
        deletionOrder.verify(postImageRepo).findById(10L);
        deletionOrder.verify(postRepo).findByIdForWrite(100L);
        deletionOrder.verify(postImageRepo).findByIdAndPostId(10L, 100L);
        deletionOrder.verify(storageStrategy).deleteChecked("cheer", "posts/100/demo.webp");
        deletionOrder.verify(postImageRepo).delete(image);
        verify(cacheManager, org.mockito.Mockito.atLeastOnce()).getCache(anyString());
    }

    @Test
    @DisplayName("게시글 이미지 삭제 실패는 미완료로 보고하고 재시도 경로를 보존한다")
    void deleteImagesByPostId_reportsCheckedStorageFailure() {
        CheerPost post = CheerPost.builder().id(100L).build();
        PostImage image = PostImage.builder()
                .id(10L)
                .post(post)
                .storagePath("posts/100/demo.webp")
                .build();
        when(postImageRepo.findByPostIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(image));
        when(config.getCheerBucket()).thenReturn("cheer");
        when(storageStrategy.deleteChecked("cheer", "posts/100/demo.webp"))
                .thenReturn(Mono.error(new StorageOperationException(
                        StorageOperationException.Kind.TRANSIENT,
                        "temporary storage failure",
                        new IllegalStateException("synthetic failure"))));

        boolean allDeleted = imageService.deleteImagesByPostId(100L);

        org.assertj.core.api.Assertions.assertThat(allDeleted).isFalse();
        verify(storageStrategy).deleteChecked("cheer", "posts/100/demo.webp");
        verify(storageStrategy, never()).delete(anyString(), anyString());
        verify(postImageRepo, never()).delete(image);
    }

    @Test
    @DisplayName("I-05: 삭제된 게시글의 이미지 목록은 다시 공개되지 않는다")
    void listPostImages_rejectsSoftDeletedPost() {
        when(postRepo.findByIdForWrite(100L)).thenReturn(Optional.empty());

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

        when(postRepo.findByIdForWrite(100L)).thenReturn(Optional.of(post));
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
        UserEntity postOwner = UserEntity.builder().id(1L).role("ROLE_USER").build();
        CheerPost deletedPost = CheerPost.builder().id(100L).author(postOwner).build();
        PostImage image = PostImage.builder()
                .id(10L)
                .post(deletedPost)
                .storagePath("posts/100/demo.webp")
                .mimeType("image/webp")
                .bytes(1024L)
                .isThumbnail(false)
                .build();

        when(currentUser.get()).thenReturn(postOwner);
        when(postImageRepo.findById(10L)).thenReturn(Optional.of(image));
        when(postRepo.findByIdForWrite(100L)).thenReturn(Optional.empty());

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

        assertThrows(AccessDeniedException.class, () -> imageService.renewSignedUrl(10L));
        // 권한 없는 요청은 게시글 쓰기 락을 잡기 전에 거절된다.
        verify(postRepo, never()).findByIdForWrite(any());
        verify(storageStrategy, never()).getUrl(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("권한 없는 썸네일 지정 요청은 게시글 쓰기 락을 잡기 전에 거절된다")
    void markAsThumbnail_forbidden_for_non_owner_without_taking_lock() {
        UserEntity postOwner = UserEntity.builder().id(1L).role("ROLE_USER").build();
        UserEntity attacker = UserEntity.builder().id(2L).role("ROLE_USER").build();
        CheerPost post = CheerPost.builder().id(100L).author(postOwner).build();
        PostImage image = PostImage.builder().id(10L).post(post).storagePath("posts/100/demo.webp")
                .mimeType("image/webp").bytes(1024L).isThumbnail(false).build();

        when(currentUser.get()).thenReturn(attacker);
        when(postImageRepo.findById(10L)).thenReturn(Optional.of(image));

        assertThrows(AccessDeniedException.class, () -> imageService.markAsThumbnail(10L));

        verify(postRepo, never()).findByIdForWrite(any());
        verify(postImageRepo, never()).save(any(PostImage.class));
    }

    @Test
    @DisplayName("소프트 삭제된 게시글의 지연 프록시를 읽다 EntityNotFound 가 나면 락 이전에도 404 로 응답한다")
    void renewSignedUrlAndThumbnail_mapDeletedPostProxyToNotFoundBeforeLock() {
        UserEntity viewer = UserEntity.builder().id(2L).role("ROLE_USER").build();
        CheerPost deletedProxy = org.mockito.Mockito.mock(CheerPost.class);
        when(deletedProxy.getId()).thenReturn(100L);
        when(deletedProxy.getAuthor()).thenThrow(new jakarta.persistence.EntityNotFoundException("deleted"));
        PostImage image = PostImage.builder().id(10L).post(deletedProxy).storagePath("posts/100/demo.webp")
                .mimeType("image/webp").bytes(1024L).isThumbnail(false).build();

        when(currentUser.get()).thenReturn(viewer);
        when(postImageRepo.findById(10L)).thenReturn(Optional.of(image));

        NotFoundBusinessException renew = assertThrows(NotFoundBusinessException.class,
                () -> imageService.renewSignedUrl(10L));
        NotFoundBusinessException thumbnail = assertThrows(NotFoundBusinessException.class,
                () -> imageService.markAsThumbnail(10L));

        org.assertj.core.api.Assertions.assertThat(renew.getCode()).isEqualTo("CHEER_POST_NOT_FOUND");
        org.assertj.core.api.Assertions.assertThat(thumbnail.getCode()).isEqualTo("CHEER_POST_NOT_FOUND");
        verify(postRepo, never()).findByIdForWrite(any());
    }

    @Test
    @DisplayName("삭제된 게시글의 썸네일 응답은 signed URL을 발급하지 않는다")
    void markAsThumbnail_rejectsSoftDeletedPost() {
        UserEntity postOwner = UserEntity.builder().id(1L).role("ROLE_USER").build();
        CheerPost deletedPost = CheerPost.builder().id(100L).author(postOwner).build();
        PostImage image = PostImage.builder()
                .id(10L)
                .post(deletedPost)
                .storagePath("posts/100/demo.webp")
                .mimeType("image/webp")
                .bytes(1024L)
                .isThumbnail(false)
                .build();

        when(currentUser.get()).thenReturn(postOwner);
        when(postImageRepo.findById(10L)).thenReturn(Optional.of(image));
        when(postRepo.findByIdForWrite(100L)).thenReturn(Optional.empty());

        NotFoundBusinessException ex = assertThrows(NotFoundBusinessException.class,
                () -> imageService.markAsThumbnail(10L));

        org.assertj.core.api.Assertions.assertThat(ex.getCode()).isEqualTo("CHEER_POST_NOT_FOUND");
        verify(postImageRepo, never()).save(any(PostImage.class));
        verify(storageStrategy, never()).getUrl(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("피드의 캐시된 이미지 URL도 삭제된 게시글에 대해서는 반환하지 않는다")
    void getPostImageUrlsByPostIds_doesNotReturnCachedUrlsForSoftDeletedPost() {
        when(postRepo.findByIdForWrite(100L)).thenReturn(Optional.empty());

        var result = imageService.getPostImageUrlsByPostIds(List.of(100L));

        org.assertj.core.api.Assertions.assertThat(result).containsEntry(100L, List.of());
        verify(stringValueOperations, never()).get("postImageUrls::100");
        verify(postImageRepo, never()).findByPostIdInOrderByPostIdAscCreatedAtAsc(any());
        verify(storageStrategy, never()).getUrl(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("단건 매퍼 이미지 URL도 삭제된 게시글의 stale cache를 반환하지 않는다")
    void getPostImageUrls_doesNotReturnCachedUrlsForSoftDeletedPost() {
        when(postRepo.findByIdForWrite(100L)).thenReturn(Optional.empty());

        var result = imageService.getPostImageUrls(100L);

        org.assertj.core.api.Assertions.assertThat(result).isEmpty();
        verify(stringValueOperations, never()).get("postImageUrls::100");
        verify(storageStrategy, never()).getUrl(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("URL 조회는 기존 쓰기 트랜잭션을 이어받아 게시글 잠금 재진입 교착을 피한다")
    void getPostImageUrlsByPostIds_joinsExistingWritableTransaction() {
        when(postRepo.findByIdForWrite(100L)).thenReturn(Optional.of(CheerPost.builder().id(100L).build()));
        when(stringValueOperations.get("postImageUrls::100")).thenReturn("[]");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);

        try {
            var result = imageService.getPostImageUrlsByPostIds(List.of(100L));

            org.assertj.core.api.Assertions.assertThat(result).containsEntry(100L, List.of());
            verify(transactionManager, never()).getTransaction(any(TransactionDefinition.class));
        } finally {
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    @DisplayName("읽기 전용 호출자는 이미지 URL 발급을 위해 별도 쓰기 트랜잭션을 사용한다")
    void getPostImageUrlsByPostIds_usesWritableTransactionForReadOnlyCaller() {
        when(postRepo.findByIdForWrite(100L)).thenReturn(Optional.of(CheerPost.builder().id(100L).build()));
        when(stringValueOperations.get("postImageUrls::100")).thenReturn("[]");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        try {
            var result = imageService.getPostImageUrlsByPostIds(List.of(100L));

            org.assertj.core.api.Assertions.assertThat(result).containsEntry(100L, List.of());
            org.mockito.ArgumentCaptor<TransactionDefinition> definition = org.mockito.ArgumentCaptor
                    .forClass(TransactionDefinition.class);
            verify(transactionManager).getTransaction(definition.capture());
            org.assertj.core.api.Assertions.assertThat(definition.getValue().getPropagationBehavior())
                    .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            org.assertj.core.api.Assertions.assertThat(definition.getValue().isReadOnly()).isFalse();
        } finally {
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    @DisplayName("I-08: 게시글 이미지 캐시가 손상되어도 DB fallback으로 목록 조회를 계속한다")
    void getPostImageUrlsByPostIds_fallsBackWhenCacheEntryIsCorrupted() {
        when(postRepo.findByIdForWrite(1L)).thenReturn(Optional.of(CheerPost.builder().id(1L).build()));
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
        when(postRepo.findByIdForWrite(1L)).thenReturn(Optional.of(CheerPost.builder().id(1L).build()));
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
        when(postRepo.findByIdForWrite(1L)).thenReturn(Optional.of(CheerPost.builder().id(1L).build()));
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
        when(postRepo.findByIdForWrite(1L)).thenReturn(Optional.of(CheerPost.builder().id(1L).build()));
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
        when(postRepo.findByIdForWrite(1L)).thenReturn(Optional.of(CheerPost.builder().id(1L).build()));
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
        when(postRepo.findByIdForWrite(1L)).thenReturn(Optional.of(post1));
        when(postRepo.findByIdForWrite(2L)).thenReturn(Optional.of(post2));
        when(config.getCheerBucket()).thenReturn("cheer-bucket");
        when(storageStrategy.getUrl(eq("cheer-bucket"), eq("posts/1/a.webp"), anyInt()))
                .thenReturn(Mono.just("signed-1a"));
        when(storageStrategy.getUrl(eq("cheer-bucket"), eq("posts/1/b.webp"), anyInt()))
                .thenReturn(Mono.just("signed-1b"));
        when(storageStrategy.getUrl(eq("cheer-bucket"), eq("posts/2/a.webp"), anyInt()))
                .thenReturn(Mono.just("signed-2a"));

        Map<Long, List<String>> result = imageService.getPostImageUrlsByPostIds(List.of(2L, 1L));

        org.assertj.core.api.Assertions.assertThat(result.get(1L)).containsExactly("signed-1a", "signed-1b");
        org.assertj.core.api.Assertions.assertThat(result.get(2L)).containsExactly("signed-2a");
        org.assertj.core.api.Assertions.assertThat(executor.executeCount()).isEqualTo(3);
        InOrder lockOrder = inOrder(postRepo);
        lockOrder.verify(postRepo).findByIdForWrite(1L);
        lockOrder.verify(postRepo).findByIdForWrite(2L);
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
