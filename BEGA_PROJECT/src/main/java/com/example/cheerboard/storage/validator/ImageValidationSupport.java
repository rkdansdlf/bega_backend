package com.example.cheerboard.storage.validator;

import com.example.common.image.ImageUtil;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

public final class ImageValidationSupport {

    private ImageValidationSupport() {
    }

    public static String requireOriginalFilename(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어있습니다.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("파일명이 없습니다.");
        }
        return originalFilename;
    }

    public static void validateExtension(String filename, Set<String> allowedExtensions) {
        String extension = getFileExtension(filename).toLowerCase();
        if (!allowedExtensions.contains(extension)) {
            throw new IllegalArgumentException(
                    "허용되지 않는 파일 형식입니다. 허용 형식: " + String.join(", ", allowedExtensions));
        }
    }

    public static void validateMimeType(String mimeType, Set<String> allowedMimeTypes) {
        if (mimeType == null || !allowedMimeTypes.contains(mimeType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "허용되지 않는 이미지 타입입니다. 허용 타입: " + String.join(", ", allowedMimeTypes));
        }
    }

    public static void validateSize(long bytes, long maxImageBytes) {
        if (bytes <= 0) {
            throw new IllegalArgumentException("파일 크기가 0입니다.");
        }
        if (bytes > maxImageBytes) {
            throw new IllegalArgumentException(
                    String.format("파일 크기가 너무 큽니다. 최대 크기: %d MB", maxImageBytes / 1024 / 1024));
        }
    }

    public static String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            throw new IllegalArgumentException("파일 확장자가 없습니다.");
        }
        return filename.substring(lastDotIndex + 1);
    }

    public static void validatePixelLimits(
            ImageUtil.ImageDimension imageDimension,
            int minShortSidePixels,
            int maxLongSidePixels,
            long maxTotalPixels) {
        int shortSide = Math.min(imageDimension.width(), imageDimension.height());
        int longSide = Math.max(imageDimension.width(), imageDimension.height());
        long totalPixels = (long) imageDimension.width() * imageDimension.height();

        if (minShortSidePixels > 0 && shortSide < minShortSidePixels) {
            throw new IllegalArgumentException(
                    String.format("해상도가 너무 낮습니다. 최소 %dpx x %dpx 이상이어야 합니다.",
                            minShortSidePixels, minShortSidePixels));
        }
        if (longSide > maxLongSidePixels) {
            throw new IllegalArgumentException(
                    String.format("이미지의 긴 변은 최대 %dpx 이하여야 합니다.", maxLongSidePixels));
        }
        if (totalPixels > maxTotalPixels) {
            throw new IllegalArgumentException(
                    String.format("이미지 총 픽셀 수는 최대 %,d 이하여야 합니다.", maxTotalPixels));
        }
    }
}
