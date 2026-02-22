package com.example.profile.storage.service;

import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.strategy.StorageStrategy;
import com.example.auth.repository.UserRepository;
import com.example.profile.storage.dto.ProfileImageDto;
import com.example.profile.storage.validator.ProfileImageValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;
import java.util.UUID;

/**
 * 프로필 이미지 업로드 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileImageService {

    private final StorageStrategy storageStrategy;
    private final StorageConfig config;
    private final ProfileImageValidator validator;
    private final UserRepository userRepository;
    private final com.example.common.image.ImageUtil imageUtil;

    /**
     * 프로필 이미지 업로드 (최적화된 트랜잭션 처리)
     * 1. DB 조회 (User 확인)
     * 2. 이미지 가공 및 S3 업로드 (Non-Blocking/No-Tx)
     * 3. DB 업데이트 (Tx)
     * 4. 기존 이미지 삭제 (Best-effort/Async recommended)
     */
    public ProfileImageDto uploadProfileImage(Long userId, MultipartFile file) {
        log.info("프로필 이미지 업로드 시작: userId={}, filename={}", userId, file.getOriginalFilename());
        java.util.Objects.requireNonNull(userId, "userId must not be null");

        // 1. 사용자 확인 + 기존 프로필 경로 경량 조회
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다.");
        }
        String oldProfileUrl = userRepository.findProfileImageUrlById(userId).orElse(null);

        validator.validateProfileImage(file);

        // 2. 이미지 처리 및 업로드 (DB 트랜잭션 외부)
        String uploadedPath = null;
        try {
            // 이미지 압축 및 WebP 변환
            var processed = imageUtil.processProfileImage(file);

            String filename = UUID.randomUUID() + "." + processed.getExtension();
            String storagePath = "profiles/" + userId + "/" + filename;

            // 스토리지에 업로드
            uploadedPath = storageStrategy
                    .uploadBytes(processed.getBytes(), processed.getContentType(), config.getProfileBucket(),
                            storagePath)
                    .map(path -> {
                        log.info("스토리지 업로드 성공: path={}", path);
                        return path;
                    })
                    .block();

            if (uploadedPath == null) {
                throw new RuntimeException("파일 업로드에 실패했습니다.");
            }

            // 주의: uploadedPath는 버킷명이 포함될 수 있음. getUrl에는 storagePath를 넘겨야 함 (이전 버그 수정 반영)

            // URL 생성
            String profileUrl = storageStrategy
                    .getUrl(config.getProfileBucket(), storagePath, config.getSignedUrlTtlSeconds())
                    .block();

            if (profileUrl == null || profileUrl.isEmpty()) {
                throw new RuntimeException("이미지 URL 생성에 실패했습니다.");
            }

            // 3. DB 업데이트 (트랜잭션 진입) -> URL이 아닌 경로(Key)를 저장
            updateUserProfileUrl(userId, storagePath);

            // 4. 성공 시 기존 이미지 삭제 (Best effort)
            // 기존 URL인 경우 (UserEntity에 저장된 값이 URL이었던 시절 데이터) -> 처리가 복잡하므로 path 추출 시도
            if (oldProfileUrl != null && !oldProfileUrl.isEmpty()) {
                deleteImageByUrl(oldProfileUrl);
            }

            return Objects.requireNonNull(new ProfileImageDto(
                    userId,
                    storagePath,
                    profileUrl,
                    processed.getContentType(),
                    processed.getSize()));

        } catch (Exception e) {
            log.error("프로필 이미지 업로드 실패. 롤백 처리 진행. Error: {}", e.getMessage(), e);

            // DB 업데이트 실패 또는 업로드 중 에러 발생 시: 방금 올린 파일 삭제 (Cleanup)
            if (uploadedPath != null) {
                // delete logic depends on storageStrategy implementation details
                // try best effort delete
                try {
                    storageStrategy.delete(config.getProfileBucket(), uploadedPath).block();
                } catch (Exception ex) {
                    log.warn("롤백 이미지 삭제 실패: {}", uploadedPath);
                }
            }
            throw new RuntimeException("프로필 이미지 업로드 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * DB 업데이트
     */
    protected void updateUserProfileUrl(Long userId, String profilePath) {
        int updatedRows = userRepository.updateProfileImageUrlById(userId, profilePath);
        if (updatedRows == 0) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다.");
        }
    }

    /**
     * 저장된 경로(path) 또는 URL을 기반으로 실제 접근 가능한 URL 반환
     */
    public String getProfileImageUrl(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isEmpty()) {
            return null;
        }

        // 1. 이미 http로 시작하는 URL인 경우 (외부 이미지 또는 Legacy 데이터)
        if (isHttpUrl(pathOrUrl)) {
            // 만약 우리 버킷의 Signed URL이라면, Path를 추출하여 재서명 시도 (Auto-healing)
            String extracted = extractStoragePathFromUrl(pathOrUrl);
            if (extracted != null) {
                String resolvedUrl = resolveProfilePathToUrl(extracted);
                if (resolvedUrl != null) {
                    return resolvedUrl;
                }
                // 재서명 실패 시 원본 URL을 그대로 사용 (dev/local 환경 호환)
                return pathOrUrl;
            }
            return pathOrUrl;
        }

        // 2. 경로(Path)인 경우 -> Signed URL 생성
        return resolveProfilePathToUrl(pathOrUrl);
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
            int profilesIndex = url.indexOf("/profiles/");
            if (profilesIndex != -1) {
                // "/profiles/" 의 '/' 다음부터 추출 -> "profiles/userId/..."
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
