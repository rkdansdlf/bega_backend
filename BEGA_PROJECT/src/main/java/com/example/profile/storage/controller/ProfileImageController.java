package com.example.profile.storage.controller;

import com.example.common.dto.ApiResponse;
import com.example.common.exception.AuthenticationRequiredException;
import com.example.common.exception.BadRequestBusinessException;
import com.example.profile.storage.dto.ProfileImageDto;
import com.example.profile.storage.service.ProfileImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
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
        Long userId = resolveAuthenticatedUserId();
        log.info("프로필 이미지 업로드 요청: userId={}, filename={}", userId, file.getOriginalFilename());
        ProfileImageDto result = profileImageService.uploadProfileImage(userId, file);

        return ResponseEntity.ok(
                ApiResponse.success("프로필 이미지가 업로드되었습니다.", result));
    }

    private Long resolveAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new AuthenticationRequiredException("인증이 필요합니다.");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Long principalId) {
            return principalId;
        }
        if (principal instanceof String principalText) {
            try {
                return Long.parseLong(principalText);
            } catch (NumberFormatException e) {
                throw new BadRequestBusinessException("INVALID_PRINCIPAL", "유효하지 않은 사용자 정보입니다.");
            }
        }

        throw new BadRequestBusinessException("INVALID_PRINCIPAL", "유효하지 않은 사용자 정보입니다.");
    }
}
