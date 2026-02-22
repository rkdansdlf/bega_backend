package com.example.cheerboard.storage.dto;

/**
 * 게시글 이미지 응답 DTO
 */
public record PostImageDto(
        Long id,
        String storagePath,
        String mimeType,
        Long bytes,
        Boolean isThumbnail,
        String url) {
}
