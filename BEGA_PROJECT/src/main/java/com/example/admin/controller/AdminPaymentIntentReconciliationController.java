package com.example.admin.controller;

import com.example.admin.dto.PaymentIntentReconciliationPageDto;
import com.example.admin.dto.PaymentIntentReconciliationResolutionRequest;
import com.example.admin.dto.PaymentIntentReconciliationResolutionResultDto;
import com.example.admin.dto.PaymentIntentReconciliationReviewDto;
import com.example.admin.service.PaymentIntentReconciliationAdminService;
import com.example.common.dto.ApiResponse;
import com.example.common.web.AuthenticatedUserIds;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/payment-intents/reconciliation")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentIntentReconciliationController {

    private final PaymentIntentReconciliationAdminService reconciliationAdminService;

    @GetMapping
    public ResponseEntity<ApiResponse<PaymentIntentReconciliationPageDto>> listPending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                "결제 대사 대기 목록을 조회했습니다.",
                reconciliationAdminService.listPending(page, size)));
    }

    @GetMapping("/{intentId}")
    public ResponseEntity<ApiResponse<PaymentIntentReconciliationReviewDto>> getReview(
            @PathVariable Long intentId) {
        return ResponseEntity.ok(ApiResponse.success(
                "결제 대사 기록을 조회했습니다.",
                reconciliationAdminService.getReview(intentId)));
    }

    @PostMapping("/{intentId}/resolution")
    @Operation(description = "Records an administrator-proposed reconciliation outcome only. The evidence reference and submitted provider tuple are not independently verified or fetched from a provider. Even after a second administrator reviews the proposal, this endpoint does not bind a payment key, change payment status, cancel or refund a payment, or call a provider; trusted provider proof is still required.")
    public ResponseEntity<ApiResponse<PaymentIntentReconciliationResolutionResultDto>> resolve(
            @PathVariable Long intentId,
            @Valid @RequestBody PaymentIntentReconciliationResolutionRequest request,
            @AuthenticationPrincipal Long userId) {
        PaymentIntentReconciliationResolutionResultDto result = reconciliationAdminService.resolve(
                intentId, AuthenticatedUserIds.require(userId), request);
        String message = result.awaitingIndependentReview()
                ? "대사 제안을 기록했습니다. 다른 관리자의 검토와 신뢰 가능한 provider 증명이 필요하며 결제 상태는 변경되지 않았습니다."
                : "독립 관리자 제안 검토를 기록했습니다. 신뢰 가능한 provider 증명 전까지 결제 상태는 변경되지 않았습니다.";
        return ResponseEntity.ok(ApiResponse.success(message, result));
    }
}
