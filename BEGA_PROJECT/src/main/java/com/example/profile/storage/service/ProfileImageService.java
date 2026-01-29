package com.example.profile.storage.service;

import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.strategy.StorageStrategy;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.profile.storage.dto.ProfileImageDto;
import com.example.profile.storage.validator.ProfileImageValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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

    @Transactional
    public ProfileImageDto uploadProfileImage(Long userId, MultipartFile file) {
        log.info("프로필 이미지 업로드 시작: userId={}, filename={}", userId, file.getOriginalFilename());
        java.util.Objects.requireNonNull(userId, "userId must not be null");

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        validator.validateProfileImage(file);

        if (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isEmpty()) {
            deleteOldProfileImage(user);
        }

        try {
            // 1. 이미지 압축 및 WebP 변환
            var processed = imageUtil.process(file);

            String filename = UUID.randomUUID() + "." + processed.getExtension();
            String storagePath = "profiles/" + userId + "/" + filename;

            // 스토리지에 업로드
            String uploadedPath = storageStrategy
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

            // URL 생성 (Strategy가 Signed URL 또는 Public URL 반환)
            String profileUrl = storageStrategy
                    .getUrl(config.getProfileBucket(), uploadedPath, config.getSignedUrlTtlSeconds())
                    .block();

            if (profileUrl == null || profileUrl.isEmpty()) {
                throw new RuntimeException("이미지 URL 생성에 실패했습니다.");
            }

            // DB 업데이트 (URL 저장)
            user.setProfileImageUrl(profileUrl);
            userRepository.save(user);

            return new ProfileImageDto(
                    userId,
                    uploadedPath,
                    profileUrl,
                    processed.getContentType(),
                    processed.getSize());

        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.error("DB 저장 실패 (Data Integrity): URL length={}. Error: {}",
                    (user.getProfileImageUrl() != null ? user.getProfileImageUrl().length() : "null"), e.getMessage());
            throw new RuntimeException("프로필 이미지 URL이 너무 길어 저장할 수 없습니다.", e);
        } catch (Exception e) {
            log.error("프로필 이미지 처리/업로드 실패. Error class: {}", e.getClass().getName(), e);
            throw new RuntimeException("프로필 이미지 업로드 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    private void deleteOldProfileImage(UserEntity user) {
        try {
            String oldUrl = user.getProfileImageUrl();
            String storagePath = extractStoragePathFromUrl(oldUrl);

            if (storagePath != null) {
                storageStrategy.delete(config.getProfileBucket(), storagePath).block();
                log.info("기존 프로필 이미지 삭제 완료: path={}", storagePath);
            }
        } catch (Exception e) {
            log.warn("기존 프로필 이미지 삭제 실패 (계속 진행): {}", e.getMessage());
        }
    }

    private String extractStoragePathFromUrl(String url) {
        if (url == null || url.isEmpty())
            return null;
        try {
            // OCI Path Style: https://{endpoint}/{bucket-name}/{path}
            String bucketName = config.getProfileBucket();
            if (url.contains("/" + bucketName + "/")) {
                String[] parts = url.split("/" + bucketName + "/");
                if (parts.length >= 2) {
                    // 쿼리 스트링 제거 (S3 Signed URL 대응)
                    return parts[parts.length - 1].split("\\?")[0];
                }
            }
        } catch (Exception e) {
            log.warn("URL 파싱 실패: {}", url);
        }
        return null;
    }
}