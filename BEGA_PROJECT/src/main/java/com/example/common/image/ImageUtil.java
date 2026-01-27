package com.example.common.image;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

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
     * If the file is smaller than threshold and not forcing conversion, returns
     * original.
     */
    public ProcessedImage process(MultipartFile file) throws IOException {
        String originalContentType = file.getContentType();
        byte[] originalBytes = file.getBytes();
        String originalExtension = getExtension(file.getOriginalFilename());

        // Check if we should skip processing
        if (shouldSkip(originalContentType, originalBytes.length)) {
            return new ProcessedImage(originalBytes, originalContentType, originalExtension);
        }

        // Default: Convert to WebP for optimization if it's an image
        try {
            return compressAndConvertToWebP(originalBytes);
        } catch (Exception e) {
            log.error("Image optimization failed, using original: {}", e.getMessage());
            // Fallback to original
            return new ProcessedImage(originalBytes, originalContentType, originalExtension);
        }
    }

    private boolean shouldSkip(String contentType, long size) {
        if (contentType == null || !contentType.startsWith("image/")) {
            log.debug("Non-image file, skipping processing: {}", contentType);
            return true;
        }
        if ("image/gif".equals(contentType)) {
            log.debug("GIF file, skipping processing (preserve animation)");
            return true;
        }
        // If it's already WebP and small enough, skip?
        // But re-compressing might save space. Let's stick to threshold.
        if (size <= COMPRESSION_THRESHOLD_BYTES && "image/webp".equals(contentType)) {
            log.debug("Small WebP file, skipping: {}KB", size / 1024);
            return true;
        }
        return false;
    }

    private ProcessedImage compressAndConvertToWebP(byte[] originalBytes) throws IOException {
        long originalSize = originalBytes.length;

        try (InputStream inputStream = new ByteArrayInputStream(originalBytes);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            // Resize if needed, and convert to WebP with 0.85 quality
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
            return "jpg"; // default
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024)
            return bytes + "B";
        if (bytes < 1024 * 1024)
            return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.2fMB", bytes / (1024.0 * 1024.0));
    }
}
