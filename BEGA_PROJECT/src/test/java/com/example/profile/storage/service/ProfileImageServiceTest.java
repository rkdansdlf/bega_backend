package com.example.profile.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.repository.UserRepository;
import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.strategy.StorageStrategy;
import com.example.common.exception.InternalServerBusinessException;
import com.example.common.exception.NotFoundBusinessException;
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
    private com.example.common.image.ImageOptimizationMetricsService metricsService;

    @Mock
    private MultipartFile multipartFile;

    @InjectMocks
    private ProfileImageService profileImageService;

    @BeforeEach
    void setUp() {
        lenient().when(config.getSignedUrlTtlSeconds()).thenReturn(518400);
    }

    @Test
    @DisplayName("프로필 저장 경로 정규화는 signed URL을 canonical key로 변환한다")
    void normalizeProfileStoragePath_extractsCanonicalKeyFromSignedUrl() {
        when(config.getProfileBucket()).thenReturn("profile-images");

        String normalized = profileImageService.normalizeProfileStoragePath(
                "https://cdn.example.com/profile-images/profiles/7/avatar.webp?signature=abc");

        assertThat(normalized).isEqualTo("profiles/7/avatar.webp");
    }

    @Test
    @DisplayName("프로필 업로드는 경량 조회/업데이트 경로를 사용한다")
    void uploadProfileImage_usesLightweightRepositoryMethods() throws Exception {
        // Given
        Long userId = 7L;
        String profileBucket = "profile-images";
        byte[] processedBytes = new byte[] { 1, 2, 3, 4 };
        byte[] feedProcessedBytes = new byte[] { 5, 6, 7, 8 };
        com.example.common.image.ImageUtil.ProcessedImage processedImage = new com.example.common.image.ImageUtil.ProcessedImage(
                processedBytes, "image/webp", "webp");
        com.example.common.image.ImageUtil.ProcessedImage feedProcessedImage = new com.example.common.image.ImageUtil.ProcessedImage(
                feedProcessedBytes, "image/jpeg", "jpg");

        when(config.getProfileBucket()).thenReturn(profileBucket);
        when(userRepository.existsById(userId)).thenReturn(true);
        when(userRepository.findProfileImageUrlById(userId))
                .thenReturn(java.util.Optional.of("https://cdn.example.com/profiles/7/old.webp?token=abc"));
        when(userRepository.findProfileFeedImageUrlById(userId))
                .thenReturn(java.util.Optional.of("https://cdn.example.com/profiles/7/feed/old.webp?token=def"));
        when(multipartFile.getOriginalFilename()).thenReturn("avatar.png");
        when(imageUtil.processProfileImage(multipartFile)).thenReturn(processedImage);
        when(imageUtil.processFeedProfileImage(multipartFile)).thenReturn(feedProcessedImage);
        when(storageStrategy.uploadBytes(eq(processedBytes), eq("image/webp"), eq(profileBucket), any(String.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(3, String.class)));
        when(storageStrategy.uploadBytes(eq(feedProcessedBytes), eq("image/jpeg"), eq(profileBucket), any(String.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(3, String.class)));
        when(storageStrategy.getUrl(eq(profileBucket), any(String.class), eq(518400)))
                .thenReturn(Mono.just("https://cdn.example.com/profiles/7/new.webp"));
        when(storageStrategy.delete(eq(profileBucket), eq("profiles/7/old.webp")))
                .thenReturn(Mono.empty());
        when(storageStrategy.delete(eq(profileBucket), eq("profiles/7/feed/old.webp")))
                .thenReturn(Mono.empty());
        when(userRepository.updateProfileImageUrlsById(eq(userId), any(String.class), any(String.class))).thenReturn(1);

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
        verify(userRepository).findProfileFeedImageUrlById(userId);
        verify(imageUtil).processFeedProfileImage(multipartFile);
        verify(userRepository).updateProfileImageUrlsById(
                eq(userId),
                argThat(path -> path != null && path.startsWith("profiles/7/") && !path.contains("/feed-v3/")),
                argThat(path -> path != null && path.startsWith("profiles/7/feed-v3/")));
        verify(userRepository, never()).updateProfileImageUrlById(eq(userId), any(String.class));
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("경량 업데이트 대상이 없으면 예외를 던진다")
    void updateUserProfileUrl_throwsWhenNoRowsUpdated() {
        // Given
        when(userRepository.updateProfileImageUrlsById(99L, "profiles/99/new.webp", null)).thenReturn(0);

        // When / Then
        NotFoundBusinessException ex = assertThrows(NotFoundBusinessException.class,
                () -> profileImageService.updateUserProfileUrl(99L, "profiles/99/new.webp"));
        assertThat(ex.getMessage()).isEqualTo("사용자를 찾을 수 없습니다.");
        assertThat(ex.getCode()).isEqualTo("USER_NOT_FOUND");
    }

    @Test
    @DisplayName("cheer 피드는 legacy feed 경로를 무시하고 원본 경로로 폴백한다")
    void getProfileImageUrlForCheerFeed_ignoresLegacyFeedPath() {
        String profileBucket = "profile-images";
        String originalUrl = "https://signed.example.com/profiles/7/avatar.webp";

        when(config.getProfileBucket()).thenReturn(profileBucket);
        when(storageStrategy.exists(profileBucket, "profiles/7/feed-v3/avatar.webp"))
                .thenReturn(Mono.just(false));
        when(storageStrategy.exists(profileBucket, "profiles/7/feed-v2/avatar.webp"))
                .thenReturn(Mono.just(false));
        when(storageStrategy.getUrl(profileBucket, "profiles/7/avatar.webp", 518400))
                .thenReturn(Mono.just(originalUrl));

        String resolved = profileImageService.getProfileImageUrlForCheerFeed(
                "profiles/7/avatar.webp",
                "profiles/7/feed/avatar.webp");

        assertThat(resolved).isEqualTo(originalUrl);
        verify(storageStrategy, never()).getUrl(profileBucket, "profiles/7/feed/avatar.webp", 518400);
    }

    @Test
    @DisplayName("cheer 피드는 저장된 feed-v3 경로를 최우선 사용한다")
    void getProfileImageUrlForCheerFeed_prefersStoredFeedV3Path() {
        String profileBucket = "profile-images";
        String feedUrl = "https://signed.example.com/profiles/7/feed-v3/avatar.webp";

        when(config.getProfileBucket()).thenReturn(profileBucket);
        when(storageStrategy.getUrl(profileBucket, "profiles/7/feed-v3/avatar.webp", 518400))
                .thenReturn(Mono.just(feedUrl));

        String resolved = profileImageService.getProfileImageUrlForCheerFeed(
                "profiles/7/avatar.webp",
                "profiles/7/feed-v3/avatar.webp");

        assertThat(resolved).isEqualTo(feedUrl);
        verify(storageStrategy, never()).exists(eq(profileBucket), any(String.class));
    }

    @Test
    @DisplayName("cheer 피드는 저장된 feed-v2 경로를 차선으로 사용한다")
    void getProfileImageUrlForCheerFeed_prefersStoredFeedV2PathWhenFeedV3Unavailable() {
        String profileBucket = "profile-images";
        String feedUrl = "https://signed.example.com/profiles/7/feed-v2/avatar.webp";

        when(config.getProfileBucket()).thenReturn(profileBucket);
        when(storageStrategy.getUrl(profileBucket, "profiles/7/feed-v2/avatar.webp", 518400))
                .thenReturn(Mono.just(feedUrl));

        String resolved = profileImageService.getProfileImageUrlForCheerFeed(
                "profiles/7/avatar.webp",
                "profiles/7/feed-v2/avatar.webp");

        assertThat(resolved).isEqualTo(feedUrl);
        verify(storageStrategy, never()).exists(eq(profileBucket), any(String.class));
    }

    @Test
    @DisplayName("cheer 피드는 feed-v3 후보 객체가 존재하면 해당 경로를 사용한다")
    void getProfileImageUrlForCheerFeed_usesCandidateFeedV3PathWhenAvailable() {
        String profileBucket = "profile-images";
        String feedUrl = "https://signed.example.com/profiles/7/feed-v3/avatar.webp";

        when(config.getProfileBucket()).thenReturn(profileBucket);
        when(storageStrategy.exists(profileBucket, "profiles/7/feed-v3/avatar.webp"))
                .thenReturn(Mono.just(true));
        when(storageStrategy.getUrl(profileBucket, "profiles/7/feed-v3/avatar.webp", 518400))
                .thenReturn(Mono.just(feedUrl));

        String resolved = profileImageService.getProfileImageUrlForCheerFeed("profiles/7/avatar.webp", null);

        assertThat(resolved).isEqualTo(feedUrl);
    }

    @Test
    @DisplayName("cheer 피드는 feed-v3가 없으면 feed-v2 후보 객체를 사용한다")
    void getProfileImageUrlForCheerFeed_usesCandidateFeedV2PathWhenFeedV3Missing() {
        String profileBucket = "profile-images";
        String feedUrl = "https://signed.example.com/profiles/7/feed-v2/avatar.webp";

        when(config.getProfileBucket()).thenReturn(profileBucket);
        when(storageStrategy.exists(profileBucket, "profiles/7/feed-v3/avatar.webp"))
                .thenReturn(Mono.just(false));
        when(storageStrategy.exists(profileBucket, "profiles/7/feed-v2/avatar.webp"))
                .thenReturn(Mono.just(true));
        when(storageStrategy.getUrl(profileBucket, "profiles/7/feed-v2/avatar.webp", 518400))
                .thenReturn(Mono.just(feedUrl));

        String resolved = profileImageService.getProfileImageUrlForCheerFeed("profiles/7/avatar.webp", null);

        assertThat(resolved).isEqualTo(feedUrl);
    }

    @Test
    @DisplayName("cheer 피드는 외부 소셜 프로필 URL을 고해상도 규칙으로 정규화한다")
    void getProfileImageUrlForCheerFeed_normalizesRemoteProfileUrl() {
        String rawUrl = "https://k.kakaocdn.net/dn/profile_110x110.png";

        String resolved = profileImageService.getProfileImageUrlForCheerFeed(rawUrl, null);

        assertThat(resolved).isEqualTo("https://k.kakaocdn.net/dn/profile_640x640.png");
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

    @Test
    @DisplayName("업로드 내부 예외 메시지는 외부 예외 메시지에 노출되지 않는다")
    void uploadProfileImage_masksInternalExceptionMessage() throws Exception {
        Long userId = 11L;
        String profileBucket = "profile-images";
        byte[] processedBytes = new byte[] { 7, 8, 9 };
        byte[] feedProcessedBytes = new byte[] { 3, 2, 1 };
        com.example.common.image.ImageUtil.ProcessedImage processedImage = new com.example.common.image.ImageUtil.ProcessedImage(
                processedBytes, "image/webp", "webp");
        com.example.common.image.ImageUtil.ProcessedImage feedProcessedImage = new com.example.common.image.ImageUtil.ProcessedImage(
                feedProcessedBytes, "image/jpeg", "jpg");

        when(config.getProfileBucket()).thenReturn(profileBucket);
        when(userRepository.existsById(userId)).thenReturn(true);
        when(userRepository.findProfileImageUrlById(userId)).thenReturn(java.util.Optional.empty());
        when(userRepository.findProfileFeedImageUrlById(userId)).thenReturn(java.util.Optional.empty());
        when(multipartFile.getOriginalFilename()).thenReturn("avatar.png");
        when(imageUtil.processProfileImage(multipartFile)).thenReturn(processedImage);
        when(imageUtil.processFeedProfileImage(multipartFile)).thenReturn(feedProcessedImage);
        when(storageStrategy.uploadBytes(eq(processedBytes), eq("image/webp"), eq(profileBucket), any(String.class)))
                .thenReturn(Mono.error(new RuntimeException("s3://internal/secret-path")));

        InternalServerBusinessException ex = assertThrows(InternalServerBusinessException.class,
                () -> profileImageService.uploadProfileImage(userId, multipartFile));

        assertThat(ex.getMessage()).isEqualTo("프로필 이미지 업로드 중 오류가 발생했습니다.");
        assertThat(ex.getMessage()).doesNotContain("secret-path");
        assertThat(ex.getCode()).isEqualTo("PROFILE_IMAGE_UPLOAD_FAILED");
    }
}
