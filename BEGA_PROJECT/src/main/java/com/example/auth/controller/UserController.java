package com.example.auth.controller;

import com.example.common.dto.ApiResponse;
import com.example.auth.dto.PublicUserProfileDto;
import com.example.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{userId}/profile")
    public ResponseEntity<ApiResponse> getPublicUserProfile(@PathVariable Long userId) {
        PublicUserProfileDto profile = userService.getPublicUserProfile(userId);
        return ResponseEntity.ok(ApiResponse.success("사용자 프로필 조회 성공", profile));
    }

    @GetMapping("/email-to-id")
    public ResponseEntity<ApiResponse> getUserIdByEmail(@RequestParam String email) {
        Long userId = userService.getUserIdByEmail(email);
        return ResponseEntity.ok(ApiResponse.success("사용자 ID 조회 성공", userId));
    }

    /**
     * 사용자의 소셜 연동(카카오/네이버) 여부 확인
     */
    @GetMapping("/{userId}/social-verified")
    public ResponseEntity<ApiResponse> checkSocialVerified(@PathVariable Long userId) {
        boolean verified = userService.isSocialVerified(userId);
        return ResponseEntity.ok(ApiResponse.success("소셜 연동 상태 조회", verified));
    }
}