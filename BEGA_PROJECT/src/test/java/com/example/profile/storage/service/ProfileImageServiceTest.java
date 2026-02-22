package com.example.profile.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.repository.UserRepository;
import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.strategy.StorageStrategy;
import com.example.profile.storage.validator.ProfileImageValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class ProfileImageServiceTest {

    @Mock
    private StorageStrategy storageStrategy;

    @Mock
    private StorageConfig config;

    @Mock
    private ProfileImageValidator validator;

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.example.common.image.ImageUtil imageUtil;

    @Mock
    private MultipartFile multipartFile;

    @InjectMocks
    private ProfileImageService profileImageService;

    @BeforeEach
    void setUp() {
        lenient().when(config.getSignedUrlTtlSeconds()).thenReturn(518400);
    }

    @Test
    @DisplayName("프로필 업로드는 경량 조회/업데이트 경로를 사용한다")
    void uploadProfileImage_usesLightweightRepositoryMethods() throws Exception {
        // Given
        Long userId = 7L;
        String profileBucket = "profile-images";
        byte[] processedBytes = new byte[] { 1, 2, 3, 4 };
        com.example.common.image.ImageUtil.ProcessedImage processedImage = new com.example.common.image.ImageUtil.ProcessedImage(
                processedBytes, "image/webp", "webp");

        when(config.getProfileBucket()).thenReturn(profileBucket);
        when(userRepository.existsById(userId)).thenReturn(true);
        when(userRepository.findProfileImageUrlById(userId))
                .thenReturn(java.util.Optional.of("https://cdn.example.com/profiles/7/old.webp?token=abc"));
        when(multipartFile.getOriginalFilename()).thenReturn("avatar.png");
        when(imageUtil.processProfileImage(multipartFile)).thenReturn(processedImage);
        when(storageStrategy.uploadBytes(eq(processedBytes), eq("image/webp"), eq(profileBucket), any(String.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(3, String.class)));
        when(storageStrategy.getUrl(eq(profileBucket), any(String.class), eq(518400)))
                .thenReturn(Mono.just("https://cdn.example.com/profiles/7/new.webp"));
        when(storageStrategy.delete(eq(profileBucket), eq("profiles/7/old.webp")))
                .thenReturn(Mono.empty());
        when(userRepository.updateProfileImageUrlById(eq(userId), any(String.class))).thenReturn(1);

        // When
        com.example.profile.storage.dto.ProfileImageDto dto = profileImageService.uploadProfileImage(userId, multipartFile);

        // Then
        assertThat(dto.userId()).isEqualTo(userId);
        assertThat(dto.storagePath()).startsWith("profiles/7/");
        assertThat(dto.publicUrl()).isEqualTo("https://cdn.example.com/profiles/7/new.webp");
        assertThat(dto.mimeType()).isEqualTo("image/webp");
        assertThat(dto.bytes()).isEqualTo((long) processedBytes.length);

        verify(userRepository).existsById(userId);
        verify(userRepository).findProfileImageUrlById(userId);
        verify(userRepository).updateProfileImageUrlById(eq(userId), any(String.class));
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("경량 업데이트 대상이 없으면 예외를 던진다")
    void updateUserProfileUrl_throwsWhenNoRowsUpdated() {
        // Given
        when(userRepository.updateProfileImageUrlById(99L, "profiles/99/new.webp")).thenReturn(0);

        // When / Then
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> profileImageService.updateUserProfileUrl(99L, "profiles/99/new.webp"));
        assertThat(ex.getMessage()).isEqualTo("사용자를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("URL 재서명 실패 시 원본 URL을 반환한다")
    void getProfileImageUrl_fallbackToOriginalUrlWhenResignFails() {
        // Given
        String profileBucket = "profile-images";
        String originalUrl = "https://example.supabase.co/storage/v1/object/public/profile-images/profiles/1/avatar.webp";
        when(config.getProfileBucket()).thenReturn(profileBucket);
        when(storageStrategy.getUrl(profileBucket, "profiles/1/avatar.webp", 518400))
                .thenReturn(Mono.error(new RuntimeException("sign failed")));

        // When
        String resolved = profileImageService.getProfileImageUrl(originalUrl);

        // Then
        assertThat(resolved).isEqualTo(originalUrl);
        verify(storageStrategy).getUrl(profileBucket, "profiles/1/avatar.webp", 518400);
    }

    @Test
    @DisplayName("레거시 bucket prefix 경로를 정규화해서 URL을 생성한다")
    void getProfileImageUrl_normalizesBucketPrefixedPath() {
        // Given
        String profileBucket = "profile-images";
        String bucketPrefixedPath = "profile-images/profiles/7/avatar.webp";
        String signedUrl = "https://signed.example.com/profiles/7/avatar.webp";
        when(config.getProfileBucket()).thenReturn(profileBucket);
        when(storageStrategy.getUrl(profileBucket, "profiles/7/avatar.webp", 518400))
                .thenReturn(Mono.just(signedUrl));

        // When
        String resolved = profileImageService.getProfileImageUrl(bucketPrefixedPath);

        // Then
        assertThat(resolved).isEqualTo(signedUrl);
        verify(storageStrategy).getUrl(profileBucket, "profiles/7/avatar.webp", 518400);
    }
}
