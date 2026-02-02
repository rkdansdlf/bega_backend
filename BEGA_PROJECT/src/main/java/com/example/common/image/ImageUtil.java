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

    @jakarta.annotation.PostConstruct
    public void init() {
        // Ensure ImageIO plugins (like TwelveMonkeys WebP) are scanned and registered
        javax.imageio.ImageIO.scanForPlugins();

        // Optional: Manual registration if scan fails
        try {
            Class<?> writerSpi = Class.forName("com.twelvemonkeys.imageio.plugins.webp.WebPImageWriterSpi");
            javax.imageio.spi.IIORegistry.getDefaultInstance().registerServiceProvider(
                    writerSpi.getDeclaredConstructor().newInstance());
            log.info("Manually registered TwelveMonkeys WebP Writer SPI");
        } catch (Exception e) {
            log.info(
                    "TwelveMonkeys WebP SPI registration failed: {}. This might be normal if the dependency is not on the classpath or already loaded.",
                    e.getMessage());
        }

        log.info("Available ImageIO Writers: {}",
                java.util.Arrays.toString(javax.imageio.ImageIO.getWriterFormatNames()));
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

        try {
            return compressAndConvertToWebP(originalBytes);
        } catch (Exception e) {
            log.error("Image optimization failed, using original: {}", e.getMessage());
            return new ProcessedImage(originalBytes, originalContentType, originalExtension);
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
}
