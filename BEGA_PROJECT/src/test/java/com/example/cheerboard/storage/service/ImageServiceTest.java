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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
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
    private com.example.common.image.ImageUtil imageUtil;

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
}
