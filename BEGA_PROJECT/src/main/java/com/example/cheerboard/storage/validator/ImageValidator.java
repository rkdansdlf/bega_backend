package com.example.cheerboard.storage.validator;

import com.example.cheerboard.storage.config.StorageConfig;
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
        String extension = getFileExtension(filename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                "허용되지 않는 파일 형식입니다. 허용 형식: " + String.join(", ", ALLOWED_EXTENSIONS)
            );
        }
    }

    /**
     * MIME 타입 검증
     */
    public void validateMimeType(String mimeType) {
        if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType.toLowerCase())) {
            throw new IllegalArgumentException(
                "허용되지 않는 이미지 타입입니다. 허용 타입: " + String.join(", ", ALLOWED_MIME_TYPES)
            );
        }
    }

    /**
     * 파일 크기 검증
     */
    public void validateSize(long bytes) {
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
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            throw new IllegalArgumentException("파일 확장자가 없습니다.");
        }
        return filename.substring(lastDotIndex + 1);
    }
}
