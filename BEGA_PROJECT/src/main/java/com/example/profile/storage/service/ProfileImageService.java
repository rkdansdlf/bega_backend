package com.example.profile.storage.service;

import com.example.cheerboard.storage.client.SupabaseStorageClient;
import com.example.cheerboard.storage.config.StorageConfig;
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

    private final SupabaseStorageClient storageClient;
    private final StorageConfig config;
    private final ProfileImageValidator validator;
    private final UserRepository userRepository;
    private final com.example.common.image.ImageUtil imageUtil; // ✅ Added

    @Transactional
    public ProfileImageDto uploadProfileImage(Long userId, MultipartFile file) {
        log.info("프로필 이미지 업로드 시작: userId={}, filename={}", userId, file.getOriginalFilename());

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

            // Supabase에 업로드
            SupabaseStorageClient.UploadResponse uploadResponse = storageClient
                    .uploadBytes(processed.getBytes(), processed.getContentType(), config.getProfileBucket(),
                            storagePath)
                    .block();

            if (uploadResponse == null) {
                throw new RuntimeException("파일 업로드에 실패했습니다.");
            }

            // 🔥 Signed URL 생성 (1년 유효)
            SupabaseStorageClient.SignedUrlResponse signedUrlResponse = storageClient
                    .createSignedUrl(config.getProfileBucket(), storagePath, 31536000) // 1년
                    .block();

            if (signedUrlResponse == null || signedUrlResponse.signedUrl() == null) {
                throw new RuntimeException("Signed URL 생성에 실패했습니다.");
            }

            String signedUrl = signedUrlResponse.signedUrl();
            log.info("업로드 완료: signedUrl={}", signedUrl);

            // DB 업데이트
            user.setProfileImageUrl(signedUrl);
            userRepository.save(user);

            return new ProfileImageDto(
                    userId,
                    storagePath,
                    signedUrl,
                    processed.getContentType(),
                    processed.getSize());

        } catch (Exception e) {
            log.error("프로필 이미지처리/업로드 실패", e);
            throw new RuntimeException("프로필 이미지 업로드 중 오류가 발생했습니다.", e);
        }
    }

    private void deleteOldProfileImage(UserEntity user) {
        try {
            String oldUrl = user.getProfileImageUrl();
            String storagePath = extractStoragePathFromUrl(oldUrl);

            if (storagePath != null) {
                storageClient.delete(config.getProfileBucket(), storagePath).block();
                log.info("기존 프로필 이미지 삭제 완료: path={}", storagePath);
            }
        } catch (Exception e) {
            log.warn("기존 프로필 이미지 삭제 실패 (계속 진행): {}", e.getMessage());
        }
    }

    private String extractStoragePathFromUrl(String url) {
        try {
            // Signed URL에서 path 추출
            // https://...supabase.co/storage/v1/object/sign/profile-images/profiles/1/uuid.jpg?token=...
            if (url.contains("/object/sign/")) {
                String[] parts = url.split("/object/sign/" + config.getProfileBucket() + "/");
                if (parts.length == 2) {
                    String pathWithQuery = parts[1];
                    // 쿼리 파라미터 제거
                    return pathWithQuery.split("\\?")[0];
                }
            }
        } catch (Exception e) {
            log.warn("URL 파싱 실패: {}", url);
        }
        return null;
    }

}