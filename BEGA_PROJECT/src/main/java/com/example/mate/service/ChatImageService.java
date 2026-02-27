package com.example.mate.service;

import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.strategy.StorageStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatImageService {

    private final StorageStrategy storageStrategy;
    private final StorageConfig storageConfig;
    private final com.example.common.image.ImageUtil imageUtil;

    public UploadResult uploadChatImage(Long userId, MultipartFile file) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 정보가 없습니다.");
        }
        validateFile(file);

        try {
            var processed = imageUtil.process(file, "mate_chat");
            String fileName = UUID.randomUUID() + "." + processed.getExtension();
            String storagePath = "chat/" + userId + "/" + fileName;

            String uploadedPath = storageStrategy
                    .uploadBytes(processed.getBytes(), processed.getContentType(), storageConfig.getCheerBucket(),
                            storagePath)
                    .block();
            if (uploadedPath == null || uploadedPath.isBlank()) {
                throw new RuntimeException("이미지 업로드에 실패했습니다.");
            }

            String signedUrl = storageStrategy
                    .getUrl(storageConfig.getCheerBucket(), storagePath, storageConfig.getSignedUrlTtlSeconds())
                    .block();
            if (signedUrl == null || signedUrl.isBlank()) {
                throw new RuntimeException("이미지 URL 생성에 실패했습니다.");
            }

            return new UploadResult(storagePath, signedUrl);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("채팅 이미지 업로드 실패: userId={}", userId, e);
            throw new RuntimeException("채팅 이미지 업로드 중 오류가 발생했습니다.", e);
        }
    }

    public String resolveChatImageUrl(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) {
            return null;
        }

        if (isHttpUrl(pathOrUrl)) {
            return pathOrUrl;
        }

        String normalizedPath = normalizeStoragePath(pathOrUrl);
        String resolved = generateSignedUrl(normalizedPath);
        if (resolved != null) {
            return resolved;
        }

        if (!normalizedPath.equals(pathOrUrl)) {
            String legacyResolved = generateSignedUrl(pathOrUrl);
            if (legacyResolved != null) {
                return legacyResolved;
            }
        }

        return pathOrUrl;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 없습니다.");
        }
        if (file.getSize() > storageConfig.getMaxImageBytes()) {
            throw new IllegalArgumentException("이미지 크기는 5MB 이하여야 합니다.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("이미지 파일만 업로드 가능합니다.");
        }
    }

    private String generateSignedUrl(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return null;
        }
        try {
            String url = storageStrategy
                    .getUrl(storageConfig.getCheerBucket(), storagePath, storageConfig.getSignedUrlTtlSeconds())
                    .block();
            if (url == null || url.isBlank()) {
                return null;
            }
            return url;
        } catch (Exception e) {
            log.warn("채팅 이미지 URL 생성 실패: path={}, error={}", storagePath, e.getMessage());
            return null;
        }
    }

    private String normalizeStoragePath(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return storagePath;
        }

        String bucket = storageConfig.getCheerBucket();
        if (bucket == null || bucket.isBlank()) {
            return storagePath;
        }

        String prefix = bucket + "/";
        if (storagePath.startsWith(prefix)) {
            return storagePath.substring(prefix.length());
        }
        return storagePath;
    }

    private boolean isHttpUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    public record UploadResult(String path, String url) {
    }
}
