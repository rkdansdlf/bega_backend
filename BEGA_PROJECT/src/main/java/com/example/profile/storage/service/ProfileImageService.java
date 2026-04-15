package com.example.profile.storage.service;

import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.BusinessException;
import com.example.common.exception.InternalServerBusinessException;
import com.example.common.exception.NotFoundBusinessException;
import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.strategy.StorageStrategy;
import com.example.auth.repository.UserRepository;
import com.example.profile.storage.dto.ProfileImageDto;
import com.example.profile.storage.validator.ProfileImageValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.UUID;

/**
 * 프로필 이미지 업로드 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileImageService {
    private static final Pattern KAKAO_PROFILE_SIZE_PATTERN =
            Pattern.compile("_(?:\\d{2,4})x(?:\\d{2,4})(?=\\.[a-zA-Z0-9]+(?:\\?|#|$))");
    private static final String KAKAO_HIGH_RES_SUFFIX = "_640x640";
    private static final Pattern GOOGLE_PROFILE_SIZE_PATTERN =
            Pattern.compile("=s\\d+(?:-c)?(?=\\.[a-zA-Z0-9]+(?:\\?|$))");
    private static final Pattern GOOGLE_PROFILE_QUERY_SIZE_PATTERN = Pattern.compile("([?&])sz=\\d+");
    private static final Pattern NAVER_PROFILE_SIZE_PATTERN =
            Pattern.compile("_(?:\\d{2,4})x(?:\\d{2,4})(?=\\.[a-zA-Z0-9]+(?:\\?|#|$))");
    private static final String GOOGLE_HIGH_RES_SIZE = "640";
    private static final String PROFILE_SIZE_SUFFIX = "640x640";
    private static final String LEGACY_FEED_SEGMENT = "feed";
    private static final String FEED_V2_SEGMENT = "feed-v2";
    private static final String FEED_V3_SEGMENT = "feed-v3";
    private static final String MEDIA_PROFILE_PREFIX = "media/profile/";
    private static final String MEDIA_PROFILE_FEED_PREFIX = "media/profile-feed/";


    private final StorageStrategy storageStrategy;
    private final StorageConfig config;
    private final ProfileImageValidator validator;
    private final UserRepository userRepository;
    private final com.example.common.image.ImageUtil imageUtil;
    private final com.example.common.image.ImageOptimizationMetricsService metricsService;

    /**
     * 프로필 이미지 업로드 (최적화된 트랜잭션 처리)
     * 1. DB 조회 (User 확인)
     * 2. 이미지 가공 및 S3 업로드 (Non-Blocking/No-Tx)
     * 3. DB 업데이트 (Tx)
     * 4. 기존 이미지 삭제 (Best-effort/Async recommended)
     */
    public ProfileImageDto uploadProfileImage(Long userId, MultipartFile file) {
        log.info("프로필 이미지 업로드 시작: userId={}, filename={}", userId, file.getOriginalFilename());
        metricsService.recordRequest("profile");
        metricsService.recordLegacyEndpoint("profile_image_upload");
        if (userId == null) {
            throw new BadRequestBusinessException("USER_ID_REQUIRED", "사용자 정보를 확인할 수 없습니다.");
        }

        // 1. 사용자 확인 + 기존 프로필 경로 경량 조회
        if (!userRepository.existsById(userId)) {
            throw new NotFoundBusinessException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다.");
        }
        String oldProfileUrl = userRepository.findProfileImageUrlById(userId).orElse(null);
        String oldProfileFeedUrl = userRepository.findProfileFeedImageUrlById(userId).orElse(null);

        try {
            validator.validateProfileImage(file);
        } catch (IllegalArgumentException e) {
            metricsService.recordReject("profile", "invalid_profile_image");
            throw new BadRequestBusinessException("INVALID_PROFILE_IMAGE", e.getMessage());
        }

        // 2. 이미지 처리 및 업로드 (DB 트랜잭션 외부)
        String uploadedProfilePath = null;
        String uploadedFeedPath = null;
        try {
            // 이미지 압축 및 WebP 변환
            var processed = imageUtil.processProfileImage(file);
            var feedProcessed = imageUtil.processFeedProfileImage(file);

            String imageId = UUID.randomUUID().toString();
            String storagePath = "profiles/" + userId + "/" + imageId + "." + processed.getExtension();
            String feedStoragePath = "profiles/" + userId + "/" + FEED_V3_SEGMENT + "/" + imageId + "." + feedProcessed.getExtension();

            // 스토리지에 업로드
            uploadedProfilePath = storageStrategy
                    .uploadBytes(processed.getBytes(), processed.getContentType(), config.getProfileBucket(),
                            storagePath)
                    .map(path -> {
                        log.info("스토리지 업로드 성공: path={}", path);
                        return path;
                    })
                    .block();

            if (uploadedProfilePath == null) {
                throw new InternalServerBusinessException("PROFILE_IMAGE_UPLOAD_FAILED", "프로필 이미지 업로드 중 오류가 발생했습니다.");
            }

            // 피드 썸네일 업로드 (실패해도 기존 동작은 유지)
            try {
                uploadedFeedPath = storageStrategy
                        .uploadBytes(feedProcessed.getBytes(), feedProcessed.getContentType(), config.getProfileBucket(),
                                feedStoragePath)
                        .map(path -> {
                            log.info("프로필 썸네일 업로드 성공: path={}", path);
                            return path;
                        })
                        .block();
            } catch (Exception e) {
                log.warn("프로필 썸네일 업로드 실패. 원본 경로로 폴백합니다. userId={}, error={}", userId,
                        e.getMessage());
            }

            // 주의: uploadedPath는 버킷명이 포함될 수 있음. getUrl에는 storagePath를 넘겨야 함 (이전 버그 수정 반영)

            // URL 생성
            String profileUrl = storageStrategy
                    .getUrl(config.getProfileBucket(), storagePath, config.getSignedUrlTtlSeconds())
                    .block();

            if (profileUrl == null || profileUrl.isEmpty()) {
                throw new InternalServerBusinessException("PROFILE_IMAGE_URL_GENERATION_FAILED", "프로필 이미지 업로드 중 오류가 발생했습니다.");
            }

            // 3. DB 업데이트 (트랜잭션 진입) -> URL이 아닌 경로(Key)를 저장
            updateUserProfileImageUrls(userId, storagePath, uploadedFeedPath != null ? feedStoragePath : null);

            // 4. 성공 시 기존 이미지 삭제 (Best effort)
            // 기존 URL인 경우 (UserEntity에 저장된 값이 URL이었던 시절 데이터) -> 처리가 복잡하므로 path 추출 시도
            if (oldProfileUrl != null && !oldProfileUrl.isEmpty()) {
                deleteImageByUrl(oldProfileUrl);
            }
            if (oldProfileFeedUrl != null && !oldProfileFeedUrl.isEmpty()) {
                deleteImageByUrl(oldProfileFeedUrl);
            }

            return Objects.requireNonNull(new ProfileImageDto(
                    userId,
                    storagePath,
                    profileUrl,
                    processed.getContentType(),
                    processed.getSize()));

        } catch (BusinessException e) {
            cleanupUploadedImage(uploadedProfilePath);
            cleanupUploadedImage(uploadedFeedPath);
            throw e;
        } catch (RuntimeException e) {
            log.error("프로필 이미지 업로드 실패. 롤백 처리 진행. Error: {}", e.getMessage(), e);
            cleanupUploadedImage(uploadedProfilePath);
            cleanupUploadedImage(uploadedFeedPath);
            throw new InternalServerBusinessException("PROFILE_IMAGE_UPLOAD_FAILED", "프로필 이미지 업로드 중 오류가 발생했습니다.");
        } catch (Exception e) {
            log.error("프로필 이미지 업로드 실패. 롤백 처리 진행. Error: {}", e.getMessage(), e);
            cleanupUploadedImage(uploadedProfilePath);
            cleanupUploadedImage(uploadedFeedPath);
            throw new InternalServerBusinessException("PROFILE_IMAGE_UPLOAD_FAILED", "프로필 이미지 업로드 중 오류가 발생했습니다.");
        }
    }

    public String normalizeProfileStoragePath(String pathOrUrl) {
        if (!StringUtils.hasText(pathOrUrl)) {
            return pathOrUrl;
        }

        String normalizedPath = extractNormalizedStoragePath(pathOrUrl);
        if (StringUtils.hasText(normalizedPath)) {
            return normalizedPath;
        }

        return pathOrUrl.strip();
    }

    /**
     * DB 업데이트
     */
    protected void updateUserProfileUrl(Long userId, String profilePath) {
        updateUserProfileImageUrls(userId, profilePath, null);
    }

    protected void updateUserProfileImageUrls(Long userId, String profilePath, String profileFeedImagePath) {
        int updatedRows = userRepository.updateProfileImageUrlsById(userId, profilePath, profileFeedImagePath);
        if (updatedRows == 0) {
            throw new NotFoundBusinessException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다.");
        }
    }

    /**
     * 저장된 경로(path) 또는 URL을 기반으로 실제 접근 가능한 URL 반환
     */
    public String getProfileImageUrl(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isEmpty()) {
            return null;
        }

        String normalizedPathOrUrl = normalizeRemoteProfileImageUrl(pathOrUrl);

        // 1. 이미 http로 시작하는 URL인 경우 (외부 이미지 또는 Legacy 데이터)
        if (isHttpUrl(normalizedPathOrUrl)) {
            // 만약 우리 버킷의 Signed URL이라면, Path를 추출하여 재서명 시도 (Auto-healing)
            String extracted = extractStoragePathFromUrl(normalizedPathOrUrl);
            if (extracted != null) {
                String resolvedUrl = resolveProfilePathToUrl(extracted);
                if (resolvedUrl != null) {
                    return resolvedUrl;
                }
                // 재서명 실패 시 원본 URL을 그대로 사용 (dev/local 환경 호환)
                return normalizedPathOrUrl;
            }
            return normalizedPathOrUrl;
        }

        // 2. 경로(Path)인 경우 -> Signed URL 생성
        return resolveProfilePathToUrl(normalizedPathOrUrl);
    }

    /**
     * cheer 피드용 아바타 URL 반환.
     * `feed-v3`를 우선 사용하고, 없으면 `feed-v2`, 마지막으로 원본/고해상도 경로로 폴백합니다.
     */
    public String getProfileImageUrlForCheerFeed(String pathOrUrl) {
        return getProfileImageUrlForCheerFeed(pathOrUrl, null);
    }

    public String getProfileImageUrlForCheerFeed(String pathOrUrl, String profileFeedImageUrl) {
        if (isTrustedFeedImagePath(profileFeedImageUrl)) {
            String resolvedFeedUrl = getProfileImageUrl(profileFeedImageUrl);
            if (resolvedFeedUrl != null && !resolvedFeedUrl.isBlank()) {
                return resolvedFeedUrl;
            }
        }

        String resolvedCandidateFeedPath = resolveProfileFeedCandidatePath(pathOrUrl);
        if (resolvedCandidateFeedPath != null) {
            String resolvedFeedUrl = generateProfileImageUrl(resolvedCandidateFeedPath);
            if (resolvedFeedUrl != null && !resolvedFeedUrl.isBlank()) {
                return resolvedFeedUrl;
            }
        }

        return getProfileImageUrl(pathOrUrl);
    }

    private String resolveProfileFeedCandidatePath(String pathOrUrl) {
        String normalizedPath = extractNormalizedStoragePath(pathOrUrl);
        if (normalizedPath == null || normalizedPath.isBlank()) {
            return null;
        }

        if (normalizedPath.startsWith(MEDIA_PROFILE_FEED_PREFIX)) {
            return normalizedPath;
        }

        if (normalizedPath.startsWith(MEDIA_PROFILE_PREFIX)) {
            String relativePath = normalizedPath.substring(MEDIA_PROFILE_PREFIX.length());
            String[] segments = relativePath.split("/");
            if (segments.length < 2) {
                return null;
            }
            return MEDIA_PROFILE_FEED_PREFIX + segments[0] + "/" + segments[1];
        }

        if (!normalizedPath.startsWith("profiles/")) {
            return null;
        }

        String[] segments = normalizedPath.split("/");
        if (segments.length < 3) {
            return null;
        }
        if (segments.length >= 4
                && (LEGACY_FEED_SEGMENT.equals(segments[2])
                || FEED_V2_SEGMENT.equals(segments[2])
                || FEED_V3_SEGMENT.equals(segments[2]))) {
            return null;
        }

        String userId = segments[1];
        String fileName = segments[segments.length - 1];
        if (userId == null || userId.isBlank() || fileName == null || fileName.isBlank()) {
            return null;
        }

        String feedV3CandidatePath = "profiles/" + userId + "/" + FEED_V3_SEGMENT + "/" + fileName;
        if (hasProfileStoragePath(feedV3CandidatePath)) {
            return feedV3CandidatePath;
        }

        String feedV2CandidatePath = "profiles/" + userId + "/" + FEED_V2_SEGMENT + "/" + fileName;
        if (hasProfileStoragePath(feedV2CandidatePath)) {
            return feedV2CandidatePath;
        }

        return null;
    }

    private boolean isTrustedFeedImagePath(String pathOrUrl) {
        String normalizedPath = extractNormalizedStoragePath(pathOrUrl);
        if (normalizedPath == null || normalizedPath.isBlank()) {
            return false;
        }

        String[] segments = normalizedPath.split("/");
        return segments.length >= 4
                && (("profiles".equals(segments[0])
                && (FEED_V3_SEGMENT.equals(segments[2]) || FEED_V2_SEGMENT.equals(segments[2])))
                || ("media".equals(segments[0]) && "profile-feed".equals(segments[1])));
    }

    private String extractNormalizedStoragePath(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) {
            return null;
        }

        String normalizedPath = isHttpUrl(pathOrUrl) ? extractStoragePathFromUrl(pathOrUrl) : pathOrUrl;
        if (normalizedPath == null || normalizedPath.isBlank()) {
            return null;
        }

        normalizedPath = normalizeStoragePath(normalizedPath).strip();
        if (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }
        return normalizedPath;
    }

    private boolean hasProfileStoragePath(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return false;
        }
        if (config.getProfileBucket() == null || config.getProfileBucket().isBlank()) {
            return false;
        }

        try {
            Boolean exists = storageStrategy.exists(config.getProfileBucket(), storagePath).block();
            return exists != null && exists;
        } catch (Exception e) {
            log.warn("프로필 썸네일 존재 여부 확인 실패: path={}, error={}", storagePath, e.getMessage());
            return false;
        }
    }

    private String resolveProfilePathToUrl(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }

        String normalizedPath = normalizeStoragePath(rawPath);
        String resolved = generateProfileImageUrl(normalizedPath);
        if (resolved != null) {
            return resolved;
        }

        if (!normalizedPath.equals(rawPath)) {
            return generateProfileImageUrl(rawPath);
        }

        return null;
    }

    private String generateProfileImageUrl(String storagePath) {
        try {
            String url = storageStrategy.getUrl(config.getProfileBucket(), storagePath, config.getSignedUrlTtlSeconds())
                    .block();
            if (url == null || url.isBlank()) {
                return null;
            }
            return url;
        } catch (Exception e) {
            log.warn("프로필 이미지 URL 생성 실패: path={}, error={}", storagePath, e.getMessage());
            return null;
        }
    }

    private String normalizeStoragePath(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return storagePath;
        }

        String bucket = config.getProfileBucket();
        if (bucket == null || bucket.isBlank()) {
            return storagePath;
        }

        String bucketPrefix = bucket + "/";
        if (storagePath.startsWith(bucketPrefix)) {
            return storagePath.substring(bucketPrefix.length());
        }

        return storagePath;
    }

    private boolean isHttpUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    // 외부 소셜 프로필 URL은 도메인 특성상 썸네일 크기 토큰이 고정되어 내려오는 경우가 있어
    // cheer 피드에서 크기 품질 저하를 줄이기 위해 표시 크기에 맞게 상향 해석합니다.
    private String normalizeRemoteProfileImageUrl(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) {
            return pathOrUrl;
        }

        String lowered = pathOrUrl.toLowerCase();

        if (lowered.contains("kakaocdn.net")) {
            return KAKAO_PROFILE_SIZE_PATTERN.matcher(pathOrUrl).replaceAll(KAKAO_HIGH_RES_SUFFIX);
        }

        if (lowered.contains("googleusercontent.com")) {
            String withFixedPathSize = GOOGLE_PROFILE_SIZE_PATTERN.matcher(pathOrUrl)
                    .replaceAll("=" + GOOGLE_HIGH_RES_SIZE + "-c");
            String withQuerySize = GOOGLE_PROFILE_QUERY_SIZE_PATTERN.matcher(withFixedPathSize)
                    .replaceAll("$1sz=" + GOOGLE_HIGH_RES_SIZE);
            if (withQuerySize.contains("googleusercontent.com")
                    && !GOOGLE_PROFILE_QUERY_SIZE_PATTERN.matcher(withQuerySize).find()) {
                String separator = withQuerySize.contains("?") ? "&" : "?";
                return withQuerySize + separator + "sz=" + GOOGLE_HIGH_RES_SIZE;
            }
            return withQuerySize;
        }

        if (lowered.contains("naver") || lowered.contains("phinf") || lowered.contains("dthumb")) {
            return NAVER_PROFILE_SIZE_PATTERN.matcher(pathOrUrl).replaceAll("_" + PROFILE_SIZE_SUFFIX);
        }

        return pathOrUrl;
    }

    private void cleanupUploadedImage(String uploadedPath) {
        if (uploadedPath == null) {
            return;
        }

        try {
            storageStrategy.delete(config.getProfileBucket(), uploadedPath).block();
        } catch (Exception ex) {
            log.warn("롤백 이미지 삭제 실패: {}", uploadedPath);
        }
    }

    private void deleteImageByUrl(String url) {
        try {
            String storagePath = extractStoragePathFromUrl(url);
            if (storagePath != null) {
                storageStrategy.delete(config.getProfileBucket(), storagePath).block();
                log.info("기존 프로필 이미지 삭제 완료: path={}", storagePath);
            }
        } catch (Exception e) {
            log.warn("기존 프로필 이미지 삭제 실패 (계속 진행): {}", e.getMessage());
        }
    }

    private String extractStoragePathFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        try {
            // 1. "profiles/" 경로 패턴을 먼저 찾음 (Bucket 이름 무관하게 동작)
            int mediaProfileFeedIndex = url.indexOf("/media/profile-feed/");
            if (mediaProfileFeedIndex != -1) {
                String pathWithQuery = url.substring(mediaProfileFeedIndex + 1);
                return pathWithQuery.split("\\?")[0];
            }

            int mediaProfileIndex = url.indexOf("/media/profile/");
            if (mediaProfileIndex != -1) {
                String pathWithQuery = url.substring(mediaProfileIndex + 1);
                return pathWithQuery.split("\\?")[0];
            }

            int profilesIndex = url.indexOf("/profiles/");
            if (profilesIndex != -1) {
                String pathWithQuery = url.substring(profilesIndex + 1);
                return pathWithQuery.split("\\?")[0];
            }

            // 2. Bucket 이름 기반 파싱 (Legacy logic backup)
            String bucketName = config.getProfileBucket();
            if (bucketName != null && !bucketName.isEmpty() && url.contains("/" + bucketName + "/")) {
                String[] parts = url.split("/" + bucketName + "/");
                if (parts.length >= 2) {
                    return parts[parts.length - 1].split("\\?")[0];
                }
            }
        } catch (Exception e) {
            log.warn("URL 파싱 실패: {}", url);
        }
        return null;
    }
}
