package com.example.cheerboard.storage.service;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 이미지 압축 컴포넌트
 * - Thumbnailator를 사용하여 이미지 리사이징 및 압축
 * - 클라이언트에서 1차 압축된 이미지를 서버에서 2차 검증/재압축
 */
@Slf4j
@Component
public class ImageCompressor {

    /** 최대 이미지 너비 (px) */
    private static final int MAX_WIDTH = 1920;

    /** 최대 이미지 높이 (px) */
    private static final int MAX_HEIGHT = 1920;

    /** 압축 품질 (0.0 ~ 1.0) */
    private static final double COMPRESSION_QUALITY = 0.85;

    /** 압축 임계값 - 이 크기 이하면 압축 건너뜀 (1MB) */
    private static final long COMPRESSION_THRESHOLD_BYTES = 1024 * 1024;

    /**
     * MultipartFile 압축
     * @param file 원본 파일
     * @return 압축된 바이트 배열
     */
    public byte[] compress(MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        long originalSize = file.getSize();

        // 이미지가 아닌 경우 원본 반환
        if (contentType == null || !contentType.startsWith("image/")) {
            log.debug("이미지가 아닌 파일, 원본 반환: contentType={}", contentType);
            return file.getBytes();
        }

        // GIF는 압축하지 않음 (애니메이션 손실 방지)
        if ("image/gif".equals(contentType)) {
            log.debug("GIF 파일, 압축 건너뜀: size={}KB", originalSize / 1024);
            return file.getBytes();
        }

        // 이미 충분히 작은 파일은 압축 건너뜀
        if (originalSize <= COMPRESSION_THRESHOLD_BYTES) {
            log.debug("파일 크기가 임계값 이하, 압축 건너뜀: size={}KB", originalSize / 1024);
            return file.getBytes();
        }

        try (InputStream inputStream = file.getInputStream();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            // 이미지 압축 및 리사이징
            Thumbnails.of(inputStream)
                    .size(MAX_WIDTH, MAX_HEIGHT)
                    .keepAspectRatio(true)
                    .outputQuality(COMPRESSION_QUALITY)
                    .outputFormat(getOutputFormat(contentType))
                    .toOutputStream(outputStream);

            byte[] compressedBytes = outputStream.toByteArray();
            long compressedSize = compressedBytes.length;
            double ratio = (1 - (double) compressedSize / originalSize) * 100;

            log.info("이미지 압축 완료: {} -> {} ({}% 감소)",
                    formatSize(originalSize), formatSize(compressedSize), String.format("%.1f", ratio));

            // 압축 후 오히려 커진 경우 원본 반환
            if (compressedSize >= originalSize) {
                log.debug("압축 후 크기 증가, 원본 반환");
                return file.getBytes();
            }

            return compressedBytes;

        } catch (IOException e) {
            log.error("이미지 압축 실패, 원본 반환: {}", e.getMessage());
            return file.getBytes();
        }
    }

    /**
     * 바이트 배열을 InputStream으로 변환하여 압축
     * @param bytes 원본 바이트 배열
     * @param contentType MIME 타입
     * @return 압축된 바이트 배열
     */
    public byte[] compress(byte[] bytes, String contentType) throws IOException {
        if (contentType == null || !contentType.startsWith("image/")) {
            return bytes;
        }

        if ("image/gif".equals(contentType)) {
            return bytes;
        }

        if (bytes.length <= COMPRESSION_THRESHOLD_BYTES) {
            return bytes;
        }

        try (InputStream inputStream = new ByteArrayInputStream(bytes);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Thumbnails.of(inputStream)
                    .size(MAX_WIDTH, MAX_HEIGHT)
                    .keepAspectRatio(true)
                    .outputQuality(COMPRESSION_QUALITY)
                    .outputFormat(getOutputFormat(contentType))
                    .toOutputStream(outputStream);

            byte[] compressedBytes = outputStream.toByteArray();

            if (compressedBytes.length >= bytes.length) {
                return bytes;
            }

            return compressedBytes;
        }
    }

    /**
     * MIME 타입에서 출력 포맷 추출
     */
    private String getOutputFormat(String contentType) {
        if (contentType == null) return "jpeg";

        return switch (contentType) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "jpeg";
        };
    }

    /**
     * 파일 크기를 읽기 쉬운 형식으로 변환
     */
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.2fMB", bytes / (1024.0 * 1024.0));
    }
}
