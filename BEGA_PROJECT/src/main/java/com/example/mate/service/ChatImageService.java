package com.example.mate.service;

import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.strategy.StorageStrategy;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.InternalServerBusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatImageService {

    private final StorageStrategy storageStrategy;
    private final StorageConfig storageConfig;
    private final com.example.common.image.ImageUtil imageUtil;
    private final com.example.common.image.ImageOptimizationMetricsService metricsService;

    public UploadResult uploadChatImage(Long userId, MultipartFile file) {
        metricsService.recordRequest("mate_chat");
        metricsService.recordLegacyEndpoint("chat_image_upload");
        if (userId == null) {
            throw new BadRequestBusinessException("USER_ID_REQUIRED", "사용자 정보를 확인할 수 없습니다.");
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
                throw new InternalServerBusinessException("CHAT_IMAGE_UPLOAD_FAILED", "채팅 이미지 업로드에 실패했습니다.");
            }

            String signedUrl = storageStrategy
                    .getUrl(storageConfig.getCheerBucket(), storagePath, storageConfig.getSignedUrlTtlSeconds())
                    .block();
            if (signedUrl == null || signedUrl.isBlank()) {
                throw new InternalServerBusinessException("CHAT_IMAGE_URL_GENERATION_FAILED", "채팅 이미지 URL 생성에 실패했습니다.");
            }

            return new UploadResult(storagePath, signedUrl);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("채팅 이미지 업로드 실패: userId={}", userId, e);
            throw new InternalServerBusinessException("CHAT_IMAGE_UPLOAD_FAILED", "채팅 이미지 업로드에 실패했습니다.");
        }
    }

    public String resolveChatImageUrl(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) {
            return null;
        }

        String normalizedPath = normalizeChatStoragePath(pathOrUrl);
        if (normalizedPath == null || normalizedPath.isBlank()) {
            return null;
        }
        if (isHttpUrl(normalizedPath)) {
            return normalizedPath;
        }
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

    public String normalizeChatStoragePath(String pathOrUrl) {
        if (!StringUtils.hasText(pathOrUrl)) {
            return null;
        }

        String normalized = pathOrUrl.strip();
        if (isHttpUrl(normalized)) {
            String extracted = extractStoragePathFromUrl(normalized);
            if (StringUtils.hasText(extracted)) {
                normalized = extracted;
            } else {
                return normalized;
            }
        }

        normalized = normalizeStoragePath(normalized);
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            metricsService.recordReject("mate_chat", "file_required");
            throw new BadRequestBusinessException("CHAT_IMAGE_FILE_REQUIRED", "업로드할 파일이 없습니다.");
        }
        if (file.getSize() > storageConfig.getMaxImageBytes()) {
            metricsService.recordReject("mate_chat", "file_too_large");
            throw new BadRequestBusinessException("CHAT_IMAGE_FILE_TOO_LARGE", "이미지 크기는 5MB 이하여야 합니다.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            metricsService.recordReject("mate_chat", "invalid_type");
            throw new BadRequestBusinessException("CHAT_IMAGE_INVALID_TYPE", "이미지 파일만 업로드 가능합니다.");
        }
        com.example.common.image.ImageUtil.ImageDimension imageDimension = imageUtil.getImageDimension(file);
        int longSide = Math.max(imageDimension.width(), imageDimension.height());
        long totalPixels = (long) imageDimension.width() * imageDimension.height();
        if (longSide > storageConfig.getMaxImageLongSidePixels()) {
            metricsService.recordReject("mate_chat", "long_side_exceeded");
            throw new BadRequestBusinessException("CHAT_IMAGE_DIMENSION_TOO_LARGE",
                    "이미지의 긴 변은 4096px 이하여야 합니다.");
        }
        if (totalPixels > storageConfig.getMaxImageTotalPixels()) {
            metricsService.recordReject("mate_chat", "total_pixels_exceeded");
            throw new BadRequestBusinessException("CHAT_IMAGE_DIMENSION_TOO_LARGE",
                    "이미지 총 픽셀 수는 16000000 이하여야 합니다.");
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

    private String extractStoragePathFromUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }

        int mediaChatIndex = url.indexOf("/media/chat/");
        if (mediaChatIndex != -1) {
            return url.substring(mediaChatIndex + 1).split("\\?")[0];
        }

        int chatIndex = url.indexOf("/chat/");
        if (chatIndex != -1) {
            return url.substring(chatIndex + 1).split("\\?")[0];
        }

        String bucket = storageConfig.getCheerBucket();
        if (StringUtils.hasText(bucket) && url.contains("/" + bucket + "/")) {
            String[] parts = url.split("/" + bucket + "/");
            if (parts.length >= 2) {
                return parts[parts.length - 1].split("\\?")[0];
            }
        }
        return null;
    }

    private boolean isHttpUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    public record UploadResult(String path, String url) {
    }
}
