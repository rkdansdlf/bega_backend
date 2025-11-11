package com.example.profile.storage.controller;

import com.example.demo.dto.ApiResponse;
import com.example.profile.storage.dto.ProfileImageDto;
import com.example.profile.storage.service.ProfileImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 프로필 이미지 업로드 API
 */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@Slf4j
public class ProfileImageController {

    private final ProfileImageService profileImageService;

    /**
     * 프로필 이미지 업로드
     * POST /api/profile/image
     */
    @PostMapping("/image")
    public ResponseEntity<ApiResponse> uploadProfileImage(
        @RequestParam("file") MultipartFile file
    ) {
        try {
            // 🔥 SecurityContext에서 userId 추출 (JWT 필터가 설정해놓음)
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            Long userId = (Long) authentication.getPrincipal();
            
            log.info("프로필 이미지 업로드 요청: userId={}, filename={}", userId, file.getOriginalFilename());
            
            ProfileImageDto result = profileImageService.uploadProfileImage(userId, file);
            
            return ResponseEntity.ok(
                ApiResponse.success("프로필 이미지가 업로드되었습니다.", result)
            );

        } catch (IllegalArgumentException e) {
            log.warn("프로필 이미지 업로드 검증 실패: error={}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("프로필 이미지 업로드 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("프로필 이미지 업로드 중 오류가 발생했습니다."));
        }
    }
}