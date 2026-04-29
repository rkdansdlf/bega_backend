package com.example.admin.controller;

import com.example.admin.dto.AuditLogDto;
import com.example.admin.dto.RoleChangeRequestDto;
import com.example.admin.dto.RoleChangeResponseDto;
import com.example.admin.service.AdminRoleService;
import com.example.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 어드민 역할 관리 API 컨트롤러
 * SUPER_ADMIN 전용 엔드포인트
 */
@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminRoleController {

    private final AdminRoleService adminRoleService;

    /**
     * 사용자를 ADMIN으로 승격
     * POST /api/admin/roles/users/{userId}/promote
     */
    @PostMapping("/users/{userId}/promote")
    public ResponseEntity<ApiResponse> promoteToAdmin(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long userId,
            @RequestBody(required = false) RoleChangeRequestDto request) {

        String reason = (request != null) ? request.getReason() : null;
        RoleChangeResponseDto response = adminRoleService.promoteToAdmin(adminId, userId, reason);

        return ResponseEntity.ok(ApiResponse.success(
                String.format("사용자 '%s'을(를) 관리자로 승격했습니다.", response.getName()),
                response
        ));
    }

    /**
     * ADMIN을 USER로 강등
     * POST /api/admin/roles/users/{userId}/demote
     */
    @PostMapping("/users/{userId}/demote")
    public ResponseEntity<ApiResponse> demoteToUser(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long userId,
            @RequestBody(required = false) RoleChangeRequestDto request) {

        String reason = (request != null) ? request.getReason() : null;
        RoleChangeResponseDto response = adminRoleService.demoteToUser(adminId, userId, reason);

        return ResponseEntity.ok(ApiResponse.success(
                String.format("사용자 '%s'을(를) 일반 사용자로 강등했습니다.", response.getName()),
                response
        ));
    }

    /**
     * 감사 로그 조회 (페이징)
     * GET /api/admin/roles/audit-logs?page=0&size=20
     *
     * 비페이징 엔드포인트(/audit-logs)는 운영 시 전체 테이블 스캔 위험으로 제거됨.
     * 기존 비페이징 호출도 page/size 파라미터 없이 그대로 호출하면 size=20 기본값으로 동작.
     */
    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse> getAuditLogs(
            @AuthenticationPrincipal Long adminId,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<AuditLogDto> logs = adminRoleService.getAuditLogsPaged(adminId, pageable);

        return ResponseEntity.ok(ApiResponse.success("감사 로그 조회 성공", logs));
    }
}
