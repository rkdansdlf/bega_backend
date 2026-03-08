package com.example.profile.storage.controller;

import com.example.common.dto.ApiResponse;
import com.example.profile.storage.dto.ProfileImageDto;
import com.example.profile.storage.service.ProfileImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
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
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse> uploadProfileImage(
            @RequestPart("file") MultipartFile file) {
        try {
            // 🔥 SecurityContext에서 userId 추출 (JWT 필터가 설정해놓음)
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || authentication.getPrincipal() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("인증되지 않은 사용자입니다."));
            }

            Object principal = authentication.getPrincipal();
            Long userId;

            if (principal instanceof Long principalId) {
                userId = principalId;
            } else if (principal instanceof String principalText) {
                try {
                    userId = Long.parseLong(principalText);
                } catch (NumberFormatException e) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(ApiResponse.error("유효하지 않은 사용자 정보입니다."));
                }
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("유효하지 않은 사용자 정보입니다."));
            }

            if (file == null || file.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("업로드할 파일이 없습니다."));
            }

            log.info("프로필 이미지 업로드 요청: userId={}, filename={}", userId, file.getOriginalFilename());

            ProfileImageDto result = profileImageService.uploadProfileImage(userId, file);

            return ResponseEntity.ok(
                    ApiResponse.success("프로필 이미지가 업로드되었습니다.", result));

        } catch (IllegalArgumentException e) {
            log.warn("프로필 이미지 업로드 검증 실패: error={}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("프로필 이미지 업로드 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("프로필 이미지 업로드 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."));
        }
    }
}
