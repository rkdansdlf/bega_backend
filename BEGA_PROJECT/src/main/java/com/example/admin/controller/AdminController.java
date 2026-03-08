package com.example.admin.controller;

import com.example.admin.dto.AdminMateDto;
import com.example.admin.dto.AdminPostDto;
import com.example.admin.dto.AdminReportActionReq;
import com.example.admin.dto.AdminReportAppealReq;
import com.example.admin.dto.AdminReportDto;
import com.example.admin.dto.AdminStatsDto;
import com.example.admin.dto.AdminUserDto;
import com.example.admin.service.AdminService;
import com.example.common.dto.ApiResponse;
import com.example.prediction.GameInningScoreRequestDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 관리자 API 컨트롤러
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@Validated
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

    @GetMapping("/reports")
    public ResponseEntity<ApiResponse> getReports(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AdminReportDto> reports = adminService.getReports(status, reason, fromDate, toDate, page, size);
        return ResponseEntity.ok(ApiResponse.success("신고 케이스 조회 성공", reports));
    }

    @GetMapping("/reports/{reportId}")
    public ResponseEntity<ApiResponse> getReport(@PathVariable Long reportId) {
        AdminReportDto report = adminService.getReport(reportId);
        return ResponseEntity.ok(ApiResponse.success("신고 케이스 상세 조회 성공", report));
    }

    @PatchMapping("/reports/{reportId}")
    public ResponseEntity<ApiResponse> handleReport(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long reportId,
            @RequestBody AdminReportActionReq req) {
        AdminReportDto result = adminService.handleReport(reportId, req, adminId);
        return ResponseEntity.ok(ApiResponse.success("신고 케이스 조치 완료", result));
    }

    @PostMapping("/reports/{reportId}/appeal")
    public ResponseEntity<ApiResponse> requestAppeal(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long reportId,
            @RequestBody(required = false) AdminReportAppealReq req) {
        AdminReportDto result = adminService.requestAppeal(reportId, req, userId);
        return ResponseEntity.ok(ApiResponse.success("이의제기 등록 완료", result));
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
    public ResponseEntity<ApiResponse> deleteUser(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long userId) {
        adminService.deleteUser(userId, adminId);
        return ResponseEntity.ok(ApiResponse.success("유저가 삭제되었습니다."));
    }

    /**
     * 응원 게시글 삭제
     * DELETE /api/admin/posts/{postId}
     */
    @DeleteMapping("/posts/{postId}")
    public ResponseEntity<ApiResponse> deletePost(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long postId) {
        adminService.deletePost(postId, adminId);
        return ResponseEntity.ok(ApiResponse.success("게시글이 삭제되었습니다."));
    }

    /**
     * 메이트 모임 삭제
     * DELETE /api/admin/mates/{mateId}
     */
    @DeleteMapping("/mates/{mateId}")
    public ResponseEntity<ApiResponse> deleteMate(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long mateId) {
        adminService.deleteMate(mateId, adminId);
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

    /**
     * 경기 이닝별 스코어 저장 (upsert)
     * PUT /api/admin/games/{gameId}/inning-scores
     */
    @PutMapping("/games/{gameId}/inning-scores")
    public ResponseEntity<ApiResponse> upsertInningScores(
            @PathVariable String gameId,
            @Valid @RequestBody List<GameInningScoreRequestDto> scores) {
        int saved = adminService.upsertInningScores(gameId, scores);
        return ResponseEntity.ok(ApiResponse.success("이닝 스코어 저장 성공",
                Map.of("gameId", gameId, "saved", saved)));
    }
}
