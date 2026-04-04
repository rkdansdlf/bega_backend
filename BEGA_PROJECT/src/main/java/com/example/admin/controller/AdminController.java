package com.example.admin.controller;

import com.example.admin.dto.AdminMateDto;
import com.example.admin.dto.AdminPostDto;
import com.example.admin.dto.AdminReportActionReq;
import com.example.admin.dto.AdminReportAppealReq;
import com.example.admin.dto.AdminReportDto;
import com.example.admin.dto.AdminSeatViewActionReq;
import com.example.admin.dto.AdminSeatViewDto;
import com.example.admin.dto.AdminStatsDto;
import com.example.admin.dto.AdminUserDto;
import com.example.admin.service.AdminService;
import com.example.BegaDiary.Service.SeatViewService;
import com.example.common.dto.ApiResponse;
import com.example.prediction.GameInningScoreRequestDto;
import com.example.prediction.GameScoreSyncBatchResultDto;
import com.example.prediction.GameScoreSyncResultDto;
import com.example.prediction.GameStatusMismatchBatchResultDto;
import com.example.prediction.GameStatusRepairBatchResultDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
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
    private final SeatViewService seatViewService;

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

    @GetMapping("/seat-views")
    public ResponseEntity<ApiResponse> getSeatViews(
            @RequestParam(required = false) String moderationStatus,
            @RequestParam(required = false) String stadium,
            @RequestParam(required = false) String aiSuggestedLabel,
            @RequestParam(required = false) String adminLabel,
            @RequestParam(required = false) Boolean ticketVerified) {
        List<AdminSeatViewDto> seatViews = seatViewService.getAdminSeatViews(
                moderationStatus,
                stadium,
                aiSuggestedLabel,
                adminLabel,
                ticketVerified);
        return ResponseEntity.ok(ApiResponse.success("시야뷰 후보 조회 성공", seatViews));
    }

    @GetMapping("/seat-views/{seatViewId}")
    public ResponseEntity<ApiResponse> getSeatView(@PathVariable Long seatViewId) {
        AdminSeatViewDto seatView = seatViewService.getAdminSeatView(seatViewId);
        return ResponseEntity.ok(ApiResponse.success("시야뷰 후보 상세 조회 성공", seatView));
    }

    @PatchMapping("/seat-views/{seatViewId}")
    public ResponseEntity<ApiResponse> handleSeatView(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long seatViewId,
            @RequestBody AdminSeatViewActionReq req) {
        AdminSeatViewDto result = seatViewService.reviewSeatView(seatViewId, adminId, req);
        return ResponseEntity.ok(ApiResponse.success("시야뷰 후보 처리 완료", result));
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

    /**
     * 저장된 스코어/이닝 데이터를 기준으로 경기 스냅샷 동기화
     * POST /api/admin/games/{gameId}/sync-snapshot
     */
    @PostMapping("/games/{gameId}/sync-snapshot")
    public ResponseEntity<ApiResponse> syncGameSnapshot(@PathVariable String gameId) {
        GameScoreSyncResultDto result = adminService.syncGameSnapshot(gameId);
        return ResponseEntity.ok(ApiResponse.success("경기 스냅샷 동기화 성공", result));
    }

    /**
     * 날짜 범위의 경기 스냅샷 일괄 동기화
     * POST /api/admin/games/sync-snapshots?startDate=2026-03-29&endDate=2026-03-29
     */
    @PostMapping("/games/sync-snapshots")
    public ResponseEntity<ApiResponse> syncGameSnapshotsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate resolvedEndDate = endDate != null ? endDate : startDate;
        GameScoreSyncBatchResultDto result = adminService.syncGameSnapshotsByDateRange(startDate, resolvedEndDate);
        return ResponseEntity.ok(ApiResponse.success("경기 스냅샷 일괄 동기화 성공", result));
    }

    /**
     * 날짜 범위의 경기 상태 불일치 진단
     * GET /api/admin/games/status-mismatches?startDate=2026-03-29&endDate=2026-03-29
     */
    @GetMapping("/games/status-mismatches")
    public ResponseEntity<ApiResponse> getGameStatusMismatches(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate resolvedEndDate = endDate != null ? endDate : startDate;
        GameStatusMismatchBatchResultDto result = adminService.findGameStatusMismatchesByDateRange(startDate, resolvedEndDate);
        return ResponseEntity.ok(ApiResponse.success("경기 상태 불일치 조회 성공", result));
    }

    /**
     * 날짜 범위의 경기 상태 불일치를 실제로 복구
     * POST /api/admin/games/repair-status-mismatches?startDate=2026-03-29&endDate=2026-03-29&dryRun=true
     */
    @PostMapping("/games/repair-status-mismatches")
    public ResponseEntity<ApiResponse> repairGameStatusMismatches(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "true") boolean dryRun) {
        LocalDate resolvedEndDate = endDate != null ? endDate : startDate;
        GameStatusRepairBatchResultDto result = adminService
                .repairGameStatusMismatchesByDateRange(startDate, resolvedEndDate, dryRun);
        return ResponseEntity.ok(ApiResponse.success(
                dryRun ? "경기 상태 불일치 복구 시뮬레이션 성공" : "경기 상태 불일치 복구 성공",
                result
        ));
    }
}
