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
        try {
            log.info("이미지 업로드 요청: postId={}, fileCount={}", postId, files.size());
            List<PostImageDto> uploaded = imageService.uploadPostImages(postId, files);
            return ResponseEntity.status(HttpStatus.CREATED).body(uploaded);

        } catch (IllegalArgumentException e) {
            log.warn("이미지 업로드 검증 실패: postId={}, error={}", postId, e.getMessage());
            return ResponseEntity.badRequest().build();

        } catch (Exception e) {
            log.error("이미지 업로드 실패: postId={}", postId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 게시글 이미지 목록 조회
     */
    @GetMapping("/posts/{postId}/images")
    public ResponseEntity<List<PostImageDto>> listPostImages(@PathVariable Long postId) {
        try {
            List<PostImageDto> images = imageService.listPostImages(postId);
            return ResponseEntity.ok(images);

        } catch (Exception e) {
            log.error("이미지 목록 조회 실패: postId={}", postId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 서명 URL 갱신
     */
    @PostMapping("/images/{imageId}/signed-url")
    public ResponseEntity<SignedUrlDto> renewSignedUrl(@PathVariable Long imageId) {
        try {
            SignedUrlDto result = imageService.renewSignedUrl(imageId);
            return ResponseEntity.ok(result);

        } catch (java.util.NoSuchElementException e) {
            log.warn("이미지를 찾을 수 없음: imageId={}", imageId);
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            log.error("서명 URL 갱신 실패: imageId={}", imageId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 썸네일로 지정
     */
    @PostMapping("/images/{imageId}/thumbnail")
    public ResponseEntity<PostImageDto> markAsThumbnail(@PathVariable Long imageId) {
        try {
            PostImageDto result = imageService.markAsThumbnail(imageId);
            return ResponseEntity.ok(result);

        } catch (java.util.NoSuchElementException e) {
            log.warn("이미지를 찾을 수 없음: imageId={}", imageId);
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            log.error("썸네일 지정 실패: imageId={}", imageId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 이미지 삭제
     */
    @DeleteMapping("/images/{imageId}")
    public ResponseEntity<Void> deleteImage(@PathVariable Long imageId) {
        try {
            imageService.deleteImage(imageId);
            return ResponseEntity.noContent().build();

        } catch (java.util.NoSuchElementException e) {
            log.warn("이미지를 찾을 수 없음: imageId={}", imageId);
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            log.error("이미지 삭제 실패: imageId={}", imageId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
