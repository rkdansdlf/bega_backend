package com.example.media.controller;

import com.example.common.dto.ApiResponse;
import com.example.common.exception.AuthenticationRequiredException;
import com.example.media.dto.FinalizeMediaUploadResponse;
import com.example.media.dto.InitMediaUploadRequest;
import com.example.media.dto.InitMediaUploadResponse;
import com.example.media.service.MediaUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaUploadController {

    private final MediaUploadService mediaUploadService;

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/uploads/init")
    public ResponseEntity<ApiResponse> initUpload(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody InitMediaUploadRequest request) {
        InitMediaUploadResponse response = mediaUploadService.initUpload(requireUserId(userId), request);
        return ResponseEntity.ok(ApiResponse.success("미디어 업로드 준비가 완료되었습니다.", response));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/uploads/{assetId}/finalize")
    public ResponseEntity<ApiResponse> finalizeUpload(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long assetId) {
        FinalizeMediaUploadResponse response = mediaUploadService.finalizeUpload(requireUserId(userId), assetId);
        return ResponseEntity.ok(ApiResponse.success("미디어 업로드가 완료되었습니다.", response));
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/uploads/{assetId}")
    public ResponseEntity<ApiResponse> deleteUpload(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long assetId) {
        mediaUploadService.deleteUpload(requireUserId(userId), assetId);
        return ResponseEntity.ok(ApiResponse.success("미디어 업로드가 삭제되었습니다."));
    }

    private Long requireUserId(Long userId) {
        if (userId == null) {
            throw new AuthenticationRequiredException("인증이 필요합니다.");
        }
        return userId;
    }
}
