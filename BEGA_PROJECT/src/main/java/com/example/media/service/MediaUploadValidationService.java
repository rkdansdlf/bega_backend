package com.example.media.service;

import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.validator.ImageValidator;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.image.ImageUtil;
import com.example.media.dto.InitMediaUploadRequest;
import com.example.media.entity.MediaAsset;
import com.example.media.entity.MediaDomain;
import com.example.profile.storage.validator.ProfileImageValidator;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MediaUploadValidationService {

    private static final int PROFILE_MIN_SHORT_SIDE_PIXELS = 320;
    private static final Set<String> PROFILE_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> DEFAULT_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Set<String> PROFILE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> DEFAULT_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final StorageConfig storageConfig;
    private final ImageUtil imageUtil;
    private final ImageValidator imageValidator;
    private final ProfileImageValidator profileImageValidator;

    public void validateInitRequest(InitMediaUploadRequest request) {
        MediaDomain domain = request.domain();
        if (domain == null) {
            throw new BadRequestBusinessException("MEDIA_DOMAIN_REQUIRED", "업로드 도메인을 선택해주세요.");
        }

        validateDeclaredMimeType(domain, request.contentType());
        validateDeclaredExtension(domain, request.fileName());
        validateDeclaredSize(request.contentLength());
        validateDeclaredDimensions(domain, request.width(), request.height());
    }

    public void validateFinalizedUpload(MediaDomain domain, MultipartFile file) {
        try {
            if (domain == MediaDomain.PROFILE) {
                profileImageValidator.validateProfileImage(file);
            } else {
                imageValidator.validateFile(file);
            }
        } catch (IllegalArgumentException ex) {
            throw new BadRequestBusinessException("INVALID_MEDIA_UPLOAD", ex.getMessage());
        }
    }

    public ImageUtil.ImageDimension getActualDimension(byte[] bytes) {
        try {
            return imageUtil.getImageDimension(bytes);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestBusinessException("INVALID_MEDIA_IMAGE", ex.getMessage());
        }
    }

    public void validateDeclaredMatchesActual(
            MediaAsset asset,
            ImageUtil.ImageDimension actualDimension,
            long actualBytes,
            String actualContentType) {
        if (asset.getDeclaredBytes() != null && asset.getDeclaredBytes() != actualBytes) {
            throw new BadRequestBusinessException("MEDIA_UPLOAD_METADATA_MISMATCH", "업로드한 파일 크기 정보가 요청과 일치하지 않습니다.");
        }
        if (asset.getDeclaredWidth() != null && asset.getDeclaredWidth() != actualDimension.width()) {
            throw new BadRequestBusinessException("MEDIA_UPLOAD_METADATA_MISMATCH", "업로드한 이미지 너비 정보가 요청과 일치하지 않습니다.");
        }
        if (asset.getDeclaredHeight() != null && asset.getDeclaredHeight() != actualDimension.height()) {
            throw new BadRequestBusinessException("MEDIA_UPLOAD_METADATA_MISMATCH", "업로드한 이미지 높이 정보가 요청과 일치하지 않습니다.");
        }
        if (actualContentType != null && !actualContentType.isBlank()) {
            String declared = normalizeMimeType(asset.getDeclaredContentType());
            String actual = normalizeMimeType(actualContentType);
            if (!declared.equals(actual)) {
                throw new BadRequestBusinessException("MEDIA_UPLOAD_METADATA_MISMATCH", "업로드한 이미지 타입이 요청과 일치하지 않습니다.");
            }
        }
    }

    private void validateDeclaredMimeType(MediaDomain domain, String contentType) {
        String normalized = normalizeMimeType(contentType);
        Set<String> allowList = domain == MediaDomain.PROFILE ? PROFILE_MIME_TYPES : DEFAULT_MIME_TYPES;
        if (!allowList.contains(normalized)) {
            throw new BadRequestBusinessException("INVALID_MEDIA_CONTENT_TYPE", "허용되지 않는 이미지 타입입니다.");
        }
    }

    private void validateDeclaredExtension(MediaDomain domain, String fileName) {
        if (fileName == null || fileName.isBlank() || !fileName.contains(".")) {
            throw new BadRequestBusinessException("INVALID_MEDIA_FILE_NAME", "파일명이 올바르지 않습니다.");
        }
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        Set<String> allowList = domain == MediaDomain.PROFILE ? PROFILE_EXTENSIONS : DEFAULT_EXTENSIONS;
        if (!allowList.contains(extension)) {
            throw new BadRequestBusinessException("INVALID_MEDIA_FILE_EXTENSION", "허용되지 않는 파일 형식입니다.");
        }
    }

    private void validateDeclaredSize(Long contentLength) {
        if (contentLength == null || contentLength <= 0) {
            throw new BadRequestBusinessException("INVALID_MEDIA_FILE_SIZE", "파일 크기가 올바르지 않습니다.");
        }
        if (contentLength > storageConfig.getMaxImageBytes()) {
            throw new BadRequestBusinessException("INVALID_MEDIA_FILE_SIZE", "이미지 크기는 5MB 이하여야 합니다.");
        }
    }

    private void validateDeclaredDimensions(MediaDomain domain, Integer width, Integer height) {
        if (width == null || height == null || width <= 0 || height <= 0) {
            throw new BadRequestBusinessException("INVALID_MEDIA_DIMENSIONS", "이미지 크기 정보가 올바르지 않습니다.");
        }
        int longSide = Math.max(width, height);
        int shortSide = Math.min(width, height);
        long totalPixels = (long) width * height;

        if (domain == MediaDomain.PROFILE && shortSide < PROFILE_MIN_SHORT_SIDE_PIXELS) {
            throw new BadRequestBusinessException("INVALID_MEDIA_DIMENSIONS", "프로필 이미지는 최소 320px 이상이어야 합니다.");
        }
        if (longSide > storageConfig.getMaxImageLongSidePixels()) {
            throw new BadRequestBusinessException("INVALID_MEDIA_DIMENSIONS", "이미지의 긴 변은 4096px 이하여야 합니다.");
        }
        if (totalPixels > storageConfig.getMaxImageTotalPixels()) {
            throw new BadRequestBusinessException("INVALID_MEDIA_DIMENSIONS", "이미지 총 픽셀 수는 16000000 이하여야 합니다.");
        }
    }

    private String normalizeMimeType(String contentType) {
        return contentType == null ? "" : contentType.trim().toLowerCase();
    }
}
