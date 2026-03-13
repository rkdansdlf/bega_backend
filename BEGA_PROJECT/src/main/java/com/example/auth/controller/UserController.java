package com.example.auth.controller;

import com.example.common.dto.ApiResponse;
import com.example.common.exception.AuthenticationRequiredException;
import com.example.common.exception.ForbiddenBusinessException;
import com.example.auth.dto.PublicUserProfileDto;
import com.example.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile/{handle}")
    public ResponseEntity<ApiResponse> getPublicUserProfile(
            @PathVariable String handle,
            @AuthenticationPrincipal Long currentUserId) {
        PublicUserProfileDto profile = userService.getPublicUserProfileByHandle(handle, currentUserId);
        return ResponseEntity.ok(ApiResponse.success("사용자 프로필 조회 성공", profile));
    }

    /**
     * 사용자의 소셜 연동(카카오/네이버) 여부 확인
     */
    @GetMapping("/{userId}/social-verified")
    public ResponseEntity<ApiResponse> checkSocialVerified(
            @PathVariable Long userId,
            @AuthenticationPrincipal Long currentUserId) {
        if (currentUserId == null) {
            throw new AuthenticationRequiredException("인증이 필요합니다.");
        }
        if (!currentUserId.equals(userId)) {
            throw new ForbiddenBusinessException("FORBIDDEN", "본인의 소셜 연동 상태만 조회할 수 있습니다.");
        }
        boolean verified = userService.isSocialVerified(userId);
        return ResponseEntity.ok(ApiResponse.success("소셜 연동 상태 조회", verified));
    }
}
