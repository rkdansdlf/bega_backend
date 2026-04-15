package com.example.common.image;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
@RequiredArgsConstructor
public class ImageUtil {

    private static final int MAX_WIDTH = 1024;
    private static final int MAX_HEIGHT = 1024;
    private static final double COMPRESSION_QUALITY = 0.70;
    private static final int PROFILE_MAX_WIDTH = 1536;
    private static final int PROFILE_MAX_HEIGHT = 1536;
    private static final double PROFILE_COMPRESSION_QUALITY = 0.92;
    private static final int FEED_PROFILE_MAX_WIDTH = 320;
    private static final int FEED_PROFILE_MAX_HEIGHT = 320;
    // Cheer 피드 아바타는 작은 표시 크기에서 엣지 디테일 유지가 중요해서
    // 품질을 최상위로 상향해 재샘플링 후 보간 손실을 최소화합니다.
    private static final double FEED_PROFILE_COMPRESSION_QUALITY = 1.0;
    private static final long COMPRESSION_THRESHOLD_BYTES = 1024 * 1024; // 1MB

    private final ImageOptimizationMetricsService metricsService;

    private boolean webpAvailable = false;

    @jakarta.annotation.PostConstruct
    public void init() {
        // Ensure ImageIO plugins are scanned
        javax.imageio.ImageIO.scanForPlugins();

        java.util.Iterator<javax.imageio.ImageWriter> writers = javax.imageio.ImageIO
                .getImageWritersByMIMEType("image/webp");
        this.webpAvailable = writers.hasNext();
        if (!this.webpAvailable) {
            log.warn(
                    "WebP ImageWriter NOT found. WebP conversion will be skipped. (TwelveMonkeys imageio-webp is likely read-only)");
            return;
        }

        log.info("WebP ImageWriter found. WebP conversion enabled. Runtime native issues will fall back to original image.");
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
        return process(file, "default");
    }

    public ProcessedImage process(MultipartFile file, String source) throws IOException {
        String originalContentType = file.getContentType();
        byte[] originalBytes = file.getBytes();
        String originalExtension = getExtension(file.getOriginalFilename());

        if (shouldSkip(originalContentType, originalBytes.length)) {
            metricsService.record(source, "skipped");
            metricsService.recordBytes(source, originalBytes.length, originalBytes.length);
            return new ProcessedImage(originalBytes, originalContentType, originalExtension);
        }

        if (!webpAvailable) {
            metricsService.record(source, "fallback");
            metricsService.recordBytes(source, originalBytes.length, originalBytes.length);
            return new ProcessedImage(originalBytes, originalContentType, originalExtension);
        }

        try {
            ProcessedImage processed = convertToWebP(originalBytes, MAX_WIDTH, MAX_HEIGHT, COMPRESSION_QUALITY);
            metricsService.record(source, "optimized");
            metricsService.recordBytes(source, originalBytes.length, processed.getSize());
            return processed;
        } catch (UnsatisfiedLinkError e) {
            webpAvailable = false;
            log.warn("WebP native codec not available. Falling back to original image. reason={}", e.getMessage());
            metricsService.record(source, "fallback");
            metricsService.recordBytes(source, originalBytes.length, originalBytes.length);
            return new ProcessedImage(originalBytes, originalContentType, originalExtension);
        } catch (LinkageError e) {
            webpAvailable = false;
            log.warn("WebP link error. Falling back to original image. reason={}", e.getMessage());
            metricsService.record(source, "fallback");
            metricsService.recordBytes(source, originalBytes.length, originalBytes.length);
            return new ProcessedImage(originalBytes, originalContentType, originalExtension);
        } catch (Exception e) {
            log.warn("Image optimization failed. Falling back to original image.", e);
            metricsService.record(source, "fallback");
            metricsService.recordBytes(source, originalBytes.length, originalBytes.length);
            return new ProcessedImage(originalBytes, originalContentType, originalExtension);
        }
    }

    public ProcessedImage processProfileImage(MultipartFile file) throws IOException {
        return processProfileImage(file, "profile");
    }

    public ProcessedImage processFeedProfileImage(MultipartFile file) throws IOException {
        return processFeedProfileImage(file, "profile_feed");
    }

    public ProcessedImage processFeedProfileImage(MultipartFile file, String source) throws IOException {
        String originalContentType = file.getContentType();
        byte[] originalBytes = file.getBytes();
        String originalExtension = getExtension(file.getOriginalFilename());

        if (shouldSkipFeedDerivative(originalContentType)) {
            metricsService.record(source, "skipped");
            metricsService.recordBytes(source, originalBytes.length, originalBytes.length);
            return new ProcessedImage(originalBytes, originalContentType, originalExtension);
        }

        try {
            BufferedImage flattenedImage = flattenToOpaqueRgb(originalBytes);
            ProcessedImage processed = convertFeedDerivative(flattenedImage, originalBytes.length);
            metricsService.record(source, "optimized");
            metricsService.recordBytes(source, originalBytes.length, processed.getSize());
            return processed;
        } catch (Exception e) {
            log.warn("Feed image optimization failed. Falling back to original image.", e);
            metricsService.record(source, "fallback");
            metricsService.recordBytes(source, originalBytes.length, originalBytes.length);
            return new ProcessedImage(originalBytes, originalContentType, originalExtension);
        }
    }

    public ProcessedImage processProfileImage(MultipartFile file, String source) throws IOException {
        String originalContentType = file.getContentType();
        byte[] originalBytes = file.getBytes();
        String originalExtension = getExtension(file.getOriginalFilename());

        if (shouldSkip(originalContentType, originalBytes.length)) {
            metricsService.record(source, "skipped");
            metricsService.recordBytes(source, originalBytes.length, originalBytes.length);
            return new ProcessedImage(originalBytes, originalContentType, originalExtension);
        }

        if (!webpAvailable) {
            metricsService.record(source, "fallback");
            metricsService.recordBytes(source, originalBytes.length, originalBytes.length);
            return new ProcessedImage(originalBytes, originalContentType, originalExtension);
        }

        try {
            ProcessedImage processed = convertToWebP(originalBytes, PROFILE_MAX_WIDTH, PROFILE_MAX_HEIGHT, PROFILE_COMPRESSION_QUALITY);
            metricsService.record(source, "optimized");
            metricsService.recordBytes(source, originalBytes.length, processed.getSize());
            return processed;
        } catch (UnsatisfiedLinkError e) {
            webpAvailable = false;
            log.warn("WebP native codec not available. Falling back to original image. reason={}", e.getMessage());
            metricsService.record(source, "fallback");
            metricsService.recordBytes(source, originalBytes.length, originalBytes.length);
            return new ProcessedImage(originalBytes, originalContentType, originalExtension);
        } catch (LinkageError e) {
            webpAvailable = false;
            log.warn("WebP link error. Falling back to original image. reason={}", e.getMessage());
            metricsService.record(source, "fallback");
            metricsService.recordBytes(source, originalBytes.length, originalBytes.length);
            return new ProcessedImage(originalBytes, originalContentType, originalExtension);
        } catch (Exception e) {
            log.warn("Image optimization failed. Falling back to original image.", e);
            metricsService.record(source, "fallback");
            metricsService.recordBytes(source, originalBytes.length, originalBytes.length);
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

    private boolean shouldSkipFeedDerivative(String contentType) {
        if (contentType == null || !contentType.startsWith("image/")) {
            return true;
        }
        return "image/gif".equals(contentType);
    }

    private ProcessedImage convertToWebP(byte[] originalBytes, int maxWidth, int maxHeight, double quality) throws IOException {
        long originalSize = originalBytes.length;
        ImageDimension originalDimension = getImageDimension(originalBytes);
        int targetWidth = Math.max(1, Math.min(maxWidth, originalDimension.width()));
        int targetHeight = Math.max(1, Math.min(maxHeight, originalDimension.height()));

        try (InputStream inputStream = new ByteArrayInputStream(originalBytes);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Thumbnails.of(inputStream)
                    .size(targetWidth, targetHeight)
                    .keepAspectRatio(true)
                    .outputQuality(quality)
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

    private ProcessedImage convertFeedDerivative(BufferedImage flattenedImage, long originalSize) throws IOException {
        if (webpAvailable) {
            try {
                return encodeFlattenedImage(flattenedImage, originalSize, FEED_PROFILE_MAX_WIDTH, FEED_PROFILE_MAX_HEIGHT,
                        FEED_PROFILE_COMPRESSION_QUALITY, "webp", "image/webp", "webp", "Feed image processed (WebP)");
            } catch (UnsatisfiedLinkError e) {
                webpAvailable = false;
                log.warn("Feed WebP native codec not available. Falling back to JPEG. reason={}", e.getMessage());
            } catch (LinkageError e) {
                webpAvailable = false;
                log.warn("Feed WebP link error. Falling back to JPEG. reason={}", e.getMessage());
            } catch (Exception e) {
                log.warn("Feed WebP optimization failed. Falling back to JPEG. reason={}", e.getMessage());
            }
        }

        return encodeFlattenedImage(flattenedImage, originalSize, FEED_PROFILE_MAX_WIDTH, FEED_PROFILE_MAX_HEIGHT,
                FEED_PROFILE_COMPRESSION_QUALITY, "jpg", "image/jpeg", "jpg", "Feed image processed (JPEG)");
    }

    private ProcessedImage encodeFlattenedImage(BufferedImage sourceImage, long originalSize, int maxWidth, int maxHeight,
            double quality, String outputFormat, String contentType, String extension, String logLabel) throws IOException {
        int targetWidth = Math.max(1, Math.min(maxWidth, sourceImage.getWidth()));
        int targetHeight = Math.max(1, Math.min(maxHeight, sourceImage.getHeight()));

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Thumbnails.of(sourceImage)
                    .size(targetWidth, targetHeight)
                    .keepAspectRatio(true)
                    .outputQuality(quality)
                    .outputFormat(outputFormat)
                    .toOutputStream(outputStream);

            byte[] compressedBytes = outputStream.toByteArray();
            long compressedSize = compressedBytes.length;

            double ratio = (1 - (double) compressedSize / originalSize) * 100;
            log.info("{}: {} -> {} ({}% reduction)",
                    logLabel, formatSize(originalSize), formatSize(compressedSize), String.format("%.1f", ratio));

            return new ProcessedImage(compressedBytes, contentType, extension);
        }
    }

    private BufferedImage flattenToOpaqueRgb(byte[] originalBytes) throws IOException {
        try (InputStream inputStream = new ByteArrayInputStream(originalBytes)) {
            BufferedImage image = javax.imageio.ImageIO.read(inputStream);
            if (image == null) {
                throw new IllegalArgumentException("이미지 데이터를 읽을 수 없습니다.");
            }

            BufferedImage flattenedImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = flattenedImage.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, flattenedImage.getWidth(), flattenedImage.getHeight());
                graphics.drawImage(image, 0, 0, null);
            } finally {
                graphics.dispose();
            }
            return flattenedImage;
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
