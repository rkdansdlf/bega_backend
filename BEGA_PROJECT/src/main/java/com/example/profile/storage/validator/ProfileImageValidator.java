package com.example.profile.storage.validator;

import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.validator.ImageValidationSupport;
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

    private static final int MIN_SHORT_SIDE_PIXELS = 320;

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
        String originalFilename = ImageValidationSupport.requireOriginalFilename(file);

        validateExtension(originalFilename);
        validateMimeType(file.getContentType());
        validateSize(file.getSize());
        validateDimensions(file);
    }

    private void validateExtension(String filename) {
        ImageValidationSupport.validateExtension(filename, ALLOWED_EXTENSIONS);
    }

    private void validateMimeType(String mimeType) {
        ImageValidationSupport.validateMimeType(mimeType, ALLOWED_MIME_TYPES);
    }

    private void validateSize(long bytes) {
        ImageValidationSupport.validateSize(bytes, config.getMaxImageBytes());
    }

    private void validateDimensions(MultipartFile file) {
        ImageUtil.ImageDimension imageDimension = imageUtil.getImageDimension(file);
        ImageValidationSupport.validatePixelLimits(
                imageDimension,
                MIN_SHORT_SIDE_PIXELS,
                config.getMaxImageLongSidePixels(),
                config.getMaxImageTotalPixels());
    }
}
