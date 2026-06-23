package com.example.cheerboard.storage.validator;

import com.example.cheerboard.storage.config.StorageConfig;
import com.example.common.image.ImageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

/**
 * 이미지 파일 검증 컴포넌트
 * - 확장자, MIME 타입, 용량, 개수 검증
 */
@Component
@RequiredArgsConstructor
public class ImageValidator {

    private final StorageConfig config;
    private final ImageUtil imageUtil;

    /**
     * 허용된 확장자
     */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "jpg", "jpeg", "png", "webp", "gif"
    );

    /**
     * 허용된 MIME 타입
     */
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
        "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    /**
     * 파일 확장자 검증
     */
    public void validateExtension(String filename) {
        ImageValidationSupport.validateExtension(filename, ALLOWED_EXTENSIONS);
    }

    /**
     * MIME 타입 검증
     */
    public void validateMimeType(String mimeType) {
        ImageValidationSupport.validateMimeType(mimeType, ALLOWED_MIME_TYPES);
    }

    /**
     * 파일 크기 검증
     */
    public void validateSize(long bytes) {
        ImageValidationSupport.validateSize(bytes, config.getMaxImageBytes());
    }

    /**
     * 업로드 파일 개수 검증
     */
    public void validateCount(int currentCount, int newCount) {
        int totalCount = currentCount + newCount;
        if (totalCount > config.getMaxImagesPerPost()) {
            throw new IllegalArgumentException(
                String.format("이미지는 최대 %d개까지 업로드 가능합니다.",
                    config.getMaxImagesPerPost())
            );
        }
    }

    /**
     * MultipartFile 전체 검증
     */
    public void validateFile(MultipartFile file) {
        String originalFilename = ImageValidationSupport.requireOriginalFilename(file);

        validateExtension(originalFilename);
        validateMimeType(file.getContentType());
        validateSize(file.getSize());
        validateDimensions(file);
    }

    /**
     * 여러 파일 검증
     */
    public void validateFiles(List<MultipartFile> files, int currentImageCount) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 없습니다.");
        }

        validateCount(currentImageCount, files.size());

        for (MultipartFile file : files) {
            validateFile(file);
        }
    }

    /**
     * 파일 확장자 추출
     */
    public String getFileExtension(String filename) {
        return ImageValidationSupport.getFileExtension(filename);
    }

    private void validateDimensions(MultipartFile file) {
        ImageUtil.ImageDimension imageDimension;
        try {
            // ImageReader probing confirms the bytes are a supported image and exposes
            // dimensions before expensive full decode/optimization work.
            imageDimension = imageUtil.getImageDimension(file);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("이미지 파일을 읽을 수 없습니다.", ex);
        }
        ImageValidationSupport.validatePixelLimits(
                imageDimension,
                0,
                config.getMaxImageLongSidePixels(),
                config.getMaxImageTotalPixels());
    }
}
