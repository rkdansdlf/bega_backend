package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // Email로 UserId 조회
    @GetMapping("/email-to-id")
    public ResponseEntity<ApiResponse> getUserIdByEmail(@RequestParam String email) {
        try {
            Long userId = userService.getUserIdByEmail(email);
            return ResponseEntity.ok(ApiResponse.success("사용자 ID 조회 성공", userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("사용자를 찾을 수 없습니다: " + e.getMessage()));
        }
    }
}