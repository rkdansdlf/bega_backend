package com.example.cheerboard.storage.service;

import com.example.auth.entity.UserEntity;
import com.example.cheerboard.config.CurrentUser;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.service.PermissionValidator;
import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.entity.PostImage;
import com.example.cheerboard.storage.repository.PostImageRepository;
import com.example.cheerboard.storage.strategy.StorageStrategy;
import com.example.cheerboard.storage.validator.ImageValidator;
import com.example.common.exception.NotFoundBusinessException;
import java.time.Duration;
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
import org.springframework.security.access.AccessDeniedException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
}
