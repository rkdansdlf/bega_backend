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
        byte[] originalBytes = file.getBytes();

        // 압축 건너뛰기 조건 체크
        if (shouldSkipCompression(contentType, originalBytes.length)) {
            return originalBytes;
        }

        return doCompress(originalBytes, contentType);
    }

    /**
     * 바이트 배열 압축
     * @param bytes 원본 바이트 배열
     * @param contentType MIME 타입
     * @return 압축된 바이트 배열
     */
    public byte[] compress(byte[] bytes, String contentType) throws IOException {
        if (shouldSkipCompression(contentType, bytes.length)) {
            return bytes;
        }

        return doCompress(bytes, contentType);
    }

    /**
     * 압축 건너뛰기 조건 확인
     */
    private boolean shouldSkipCompression(String contentType, long size) {
        if (contentType == null || !contentType.startsWith("image/")) {
            log.debug("이미지가 아닌 파일, 압축 건너뜀: contentType={}", contentType);
            return true;
        }

        if ("image/gif".equals(contentType)) {
            log.debug("GIF 파일, 압축 건너뜀 (애니메이션 보존): size={}KB", size / 1024);
            return true;
        }

        if (size <= COMPRESSION_THRESHOLD_BYTES) {
            log.debug("파일 크기가 임계값 이하, 압축 건너뜀: size={}KB", size / 1024);
            return true;
        }

        return false;
    }

    /**
     * 실제 압축 수행
     */
    private byte[] doCompress(byte[] originalBytes, String contentType) throws IOException {
        long originalSize = originalBytes.length;

        try (InputStream inputStream = new ByteArrayInputStream(originalBytes);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Thumbnails.of(inputStream)
                    .size(MAX_WIDTH, MAX_HEIGHT)
                    .keepAspectRatio(true)
                    .outputQuality(COMPRESSION_QUALITY)
                    .outputFormat(getOutputFormat(contentType))
                    .toOutputStream(outputStream);

            byte[] compressedBytes = outputStream.toByteArray();
            long compressedSize = compressedBytes.length;

            // 압축 후 오히려 커진 경우 원본 반환
            if (compressedSize >= originalSize) {
                log.debug("압축 후 크기 증가, 원본 반환");
                return originalBytes;
            }

            double ratio = (1 - (double) compressedSize / originalSize) * 100;
            log.info("이미지 압축 완료: {} -> {} ({}% 감소)",
                    formatSize(originalSize), formatSize(compressedSize), String.format("%.1f", ratio));

            return compressedBytes;

        } catch (IOException e) {
            log.error("이미지 압축 실패, 원본 반환: {}", e.getMessage());
            return originalBytes;
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
