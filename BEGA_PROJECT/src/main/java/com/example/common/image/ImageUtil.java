package com.example.common.image;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.awt.image.BufferedImage;

/**
 * Common Image Processing Utility
 * - Provides compression and format conversion (e.g., to WebP)
 * - Uses Thumbnailator library
 */
@Slf4j
@Component
public class ImageUtil {

    private static final int MAX_WIDTH = 1024;
    private static final int MAX_HEIGHT = 1024;
    private static final double COMPRESSION_QUALITY = 0.70;
    private static final long COMPRESSION_THRESHOLD_BYTES = 1024 * 1024; // 1MB

    private boolean webpAvailable = false;

    @jakarta.annotation.PostConstruct
    public void init() {
        // Ensure ImageIO plugins are scanned
        javax.imageio.ImageIO.scanForPlugins();

        // Check availability
        java.util.Iterator<javax.imageio.ImageWriter> writers = javax.imageio.ImageIO
                .getImageWritersByMIMEType("image/webp");
        if (writers.hasNext()) {
            this.webpAvailable = true;
            log.info("WebP ImageWriter found. Image optimization enabled.");
        } else {
            this.webpAvailable = false;
            log.warn(
                    "WebP ImageWriter NOT found. WebP conversion will be skipped. (TwelveMonkeys imageio-webp is likely read-only)");
        }
    }

    public static class ProcessedImage {
        private final byte[] bytes;
        private final String contentType;
        private final String extension;

        public ProcessedImage(byte[] bytes, String contentType, String extension) {
            this.bytes = bytes;
            this.contentType = contentType;
            this.extension = extension;
        }

        public byte[] getBytes() {
            return bytes;
        }

        public String getContentType() {
            return contentType;
        }

        public String getExtension() {
            return extension;
        }

        public long getSize() {
            return bytes.length;
        }
    }

    /**
     * Compress and optionally convert to WebP
     */
    public ProcessedImage process(MultipartFile file) throws IOException {
        String originalContentType = file.getContentType();
        byte[] originalBytes = file.getBytes();
        String originalExtension = getExtension(file.getOriginalFilename());

        if (shouldSkip(originalContentType, originalBytes.length)) {
            return new ProcessedImage(originalBytes, originalContentType, originalExtension);
        }

        if (!webpAvailable) {
            return new ProcessedImage(originalBytes, originalContentType, originalExtension);
        }

        try {
            return compressAndConvertToWebP(originalBytes);
        } catch (Exception e) {
            log.warn("Image optimization failed. Falling back to original image.", e);
            return new ProcessedImage(originalBytes, originalContentType, originalExtension);
        }
    }

    public ImageDimension getImageDimension(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("이미지 파일이 비어있습니다.");
        }
        try {
            return getImageDimension(file.getBytes());
        } catch (IOException e) {
            throw new IllegalArgumentException("이미지 크기 확인 중 오류가 발생했습니다.", e);
        }
    }

    public ImageDimension getImageDimension(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("이미지 데이터가 비어있습니다.");
        }

        try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
            BufferedImage image = javax.imageio.ImageIO.read(inputStream);
            if (image == null) {
                throw new IllegalArgumentException("이미지 데이터를 읽을 수 없습니다.");
            }

            int width = image.getWidth();
            int height = image.getHeight();
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("이미지 치수가 유효하지 않습니다.");
            }

            return new ImageDimension(width, height);
        } catch (IOException e) {
            throw new IllegalArgumentException("이미지 치수 확인 실패: " + e.getMessage(), e);
        }
    }

    private boolean shouldSkip(String contentType, long size) {
        if (contentType == null || !contentType.startsWith("image/")) {
            return true;
        }
        if ("image/gif".equals(contentType)) {
            return true;
        }
        if (size <= COMPRESSION_THRESHOLD_BYTES && "image/webp".equals(contentType)) {
            return true;
        }
        return false;
    }

    private ProcessedImage compressAndConvertToWebP(byte[] originalBytes) throws IOException {
        long originalSize = originalBytes.length;

        try (InputStream inputStream = new ByteArrayInputStream(originalBytes);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Thumbnails.of(inputStream)
                    .size(MAX_WIDTH, MAX_HEIGHT)
                    .keepAspectRatio(true)
                    .outputQuality(COMPRESSION_QUALITY)
                    .outputFormat("webp")
                    .toOutputStream(outputStream);

            byte[] compressedBytes = outputStream.toByteArray();
            long compressedSize = compressedBytes.length;

            double ratio = (1 - (double) compressedSize / originalSize) * 100;
            log.info("Image processed (WebP): {} -> {} ({}% reduction)",
                    formatSize(originalSize), formatSize(compressedSize), String.format("%.1f", ratio));

            return new ProcessedImage(compressedBytes, "image/webp", "webp");
        }
    }

    private String getExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1)
            return "jpg";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024)
            return bytes + "B";
        if (bytes < 1024 * 1024)
            return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.2fMB", bytes / (1024.0 * 1024.0));
    }

    public record ImageDimension(int width, int height) {
    }
}
