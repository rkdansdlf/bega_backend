package com.example.cheerboard.storage.controller;

import com.example.cheerboard.storage.dto.PostImageDto;
import com.example.cheerboard.storage.dto.SignedUrlDto;
import com.example.cheerboard.storage.service.ImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 이미지 업로드/삭제/조회 API
 * - POST /api/posts/{postId}/images - 이미지 업로드 (최대 10개, 각 5MB)
 * - GET /api/posts/{postId}/images - 이미지 목록 조회
 * - POST /api/images/{imageId}/signed-url - 서명 URL 갱신
 * - POST /api/images/{imageId}/thumbnail - 썸네일 지정
 * - DELETE /api/images/{imageId} - 이미지 삭제
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class ImageController {

    private final ImageService imageService;

    /**
     * 게시글 이미지 업로드
     */
    @PostMapping("/posts/{postId}/images")
    public ResponseEntity<List<PostImageDto>> uploadPostImages(
        @PathVariable Long postId,
        @RequestParam("files") List<MultipartFile> files
    ) {
        int fileCount = files == null ? 0 : files.size();
        log.info("이미지 업로드 요청: postId={}, fileCount={}", postId, fileCount);
        List<PostImageDto> uploaded = imageService.uploadPostImages(postId, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(uploaded);
    }

    /**
     * 게시글 이미지 목록 조회
     */
    @GetMapping("/posts/{postId}/images")
    public ResponseEntity<List<PostImageDto>> listPostImages(@PathVariable Long postId) {
        List<PostImageDto> images = imageService.listPostImages(postId);
        return ResponseEntity.ok(images);
    }

    /**
     * 서명 URL 갱신
     */
    @PostMapping("/images/{imageId}/signed-url")
    public ResponseEntity<SignedUrlDto> renewSignedUrl(@PathVariable Long imageId) {
        SignedUrlDto result = imageService.renewSignedUrl(imageId);
        return ResponseEntity.ok(result);
    }

    /**
     * 썸네일로 지정
     */
    @PostMapping("/images/{imageId}/thumbnail")
    public ResponseEntity<PostImageDto> markAsThumbnail(@PathVariable Long imageId) {
        PostImageDto result = imageService.markAsThumbnail(imageId);
        return ResponseEntity.ok(result);
    }

    /**
     * 이미지 삭제
     */
    @DeleteMapping("/images/{imageId}")
    public ResponseEntity<Void> deleteImage(@PathVariable Long imageId) {
        imageService.deleteImage(imageId);
        return ResponseEntity.noContent().build();
    }
}
