package com.example.admin.controller;

import com.example.admin.dto.AdminMateDto;
import com.example.admin.dto.AdminPostDto;
import com.example.admin.dto.AdminStatsDto;
import com.example.admin.dto.AdminUserDto;
import com.example.admin.service.AdminService;
import com.example.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 관리자 API 컨트롤러
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')") // 🔥 관리자만 접근 가능
public class AdminController {

    private final AdminService adminService;

    /**
     * 대시보드 통계 조회
     * GET /api/admin/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse> getStats() {
        AdminStatsDto stats = adminService.getStats();
        return ResponseEntity.ok(ApiResponse.success("통계 조회 성공", stats));
    }

    /**
     * 유저 목록 조회
     * GET /api/admin/users?search=검색어
     */
    @GetMapping("/users")
    public ResponseEntity<ApiResponse> getUsers(
            @RequestParam(required = false) String search) {
        List<AdminUserDto> users = adminService.getUsers(search);
        return ResponseEntity.ok(ApiResponse.success("유저 목록 조회 성공", users));
    }

    /**
     * 게시글 목록 조회
     * GET /api/admin/posts
     */
    @GetMapping("/posts")
    public ResponseEntity<ApiResponse> getPosts() {
        List<AdminPostDto> posts = adminService.getPosts();
        return ResponseEntity.ok(ApiResponse.success("게시글 목록 조회 성공", posts));
    }

    /**
     * 메이트 목록 조회
     * GET /api/admin/mates
     */
    @GetMapping("/mates")
    public ResponseEntity<ApiResponse> getMates() {
        List<AdminMateDto> mates = adminService.getMates();
        return ResponseEntity.ok(ApiResponse.success("메이트 목록 조회 성공", mates));
    }

    /**
     * 유저 삭제
     * DELETE /api/admin/users/{userId}
     */
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<ApiResponse> deleteUser(@PathVariable Long userId) {
        adminService.deleteUser(userId);
        return ResponseEntity.ok(ApiResponse.success("유저가 삭제되었습니다."));
    }

    /**
     * 응원 게시글 삭제
     * DELETE /api/admin/posts/{postId}
     */
    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<ApiResponse> deletePost(@PathVariable Long postId) {
        adminService.deletePost(postId);
        return ResponseEntity.ok(ApiResponse.success("게시글이 삭제되었습니다."));
    }

    /**
     * 메이트 모임 삭제
     * DELETE /api/admin/mates/{mateId}
     */
    @DeleteMapping("/mates/{mateId}")
    public ResponseEntity<ApiResponse> deleteMate(@PathVariable Long mateId) {
        adminService.deleteMate(mateId);
        return ResponseEntity.ok(ApiResponse.success("메이트 모임이 삭제되었습니다."));
    }

    /**
     * 캐시 통계 조회 (관리자 전용)
     * GET /api/admin/cache-stats
     */
    @GetMapping("/cache-stats")
    public ResponseEntity<ApiResponse> getCacheStats() {
        var stats = adminService.getCacheStats();
        return ResponseEntity.ok(ApiResponse.success("캐시 통계 조회 성공", stats));
    }
}
