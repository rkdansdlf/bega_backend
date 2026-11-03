package com.example.admin.controller;

import com.example.admin.dto.MateLinkReconciliationCasePageDto;
import com.example.admin.dto.PayoutHoldPageDto;
import com.example.admin.service.MateLinkReconciliationCaseAdminService;
import com.example.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/mate-link-reconciliation-cases")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminMateLinkReconciliationCaseController {

    private final MateLinkReconciliationCaseAdminService adminService;

    @GetMapping
    public ResponseEntity<ApiResponse<MateLinkReconciliationCasePageDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                "신청·결제 연결 대사 사례를 조회했습니다.",
                adminService.list(page, size)));
    }

    @GetMapping("/payout-holds")
    public ResponseEntity<ApiResponse<PayoutHoldPageDto>> listPayoutHolds(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                "수동 대사 보류 지급을 조회했습니다.",
                adminService.listPayoutHolds(page, size)));
    }
}
