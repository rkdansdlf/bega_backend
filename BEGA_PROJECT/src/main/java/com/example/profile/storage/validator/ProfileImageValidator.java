package com.example.profile.storage.validator;

import com.example.cheerboard.storage.config.StorageConfig;
import com.example.common.image.ImageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * 프로필 이미지 검증 컴포넌트
 */
@Component
@RequiredArgsConstructor
public class ProfileImageValidator {

    private final StorageConfig config;
    private final ImageUtil imageUtil;

    private static final int MIN_SHORT_SIDE_PIXELS = 256;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "jpg", "jpeg", "png", "webp"
    );

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
        "image/jpeg", "image/png", "image/webp"
    );

    /**
     * 프로필 이미지 검증
     */
    public void validateProfileImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어있습니다.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("파일명이 없습니다.");
        }

        validateExtension(originalFilename);
        validateMimeType(file.getContentType());
        validateSize(file.getSize());
        validateDimensions(file);
    }

    private void validateExtension(String filename) {
        String extension = getFileExtension(filename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                "허용되지 않는 파일 형식입니다. 허용 형식: " + String.join(", ", ALLOWED_EXTENSIONS)
            );
        }
    }

    private void validateMimeType(String mimeType) {
        if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType.toLowerCase())) {
            throw new IllegalArgumentException(
                "허용되지 않는 이미지 타입입니다. 허용 타입: " + String.join(", ", ALLOWED_MIME_TYPES)
            );
        }
    }

    private void validateSize(long bytes) {
        if (bytes <= 0) {
            throw new IllegalArgumentException("파일 크기가 0입니다.");
        }
        if (bytes > config.getMaxImageBytes()) {
            throw new IllegalArgumentException(
                String.format("파일 크기가 너무 큽니다. 최대 크기: %d MB",
                    config.getMaxImageBytes() / 1024 / 1024)
            );
        }
    }

    private void validateDimensions(MultipartFile file) {
        ImageUtil.ImageDimension imageDimension = imageUtil.getImageDimension(file);
        int shortSide = Math.min(imageDimension.width(), imageDimension.height());
        if (shortSide < MIN_SHORT_SIDE_PIXELS) {
            throw new IllegalArgumentException(
                    String.format("해상도가 너무 낮습니다. 최소 %dpx x %dpx 이상이어야 합니다.", MIN_SHORT_SIDE_PIXELS,
                            MIN_SHORT_SIDE_PIXELS));
        }
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            throw new IllegalArgumentException("파일 확장자가 없습니다.");
        }
        return filename.substring(lastDotIndex + 1);
    }
}
