package com.example.mate.controller;

import com.example.common.dto.ApiResponse;
import com.example.mate.entity.PayoutTransaction;
import com.example.mate.service.PaymentTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/settlements")
@RequiredArgsConstructor
public class InternalSettlementController {

    private final PaymentTransactionService paymentTransactionService;

    @PostMapping("/{paymentId}/payout")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse> requestPayout(@PathVariable Long paymentId) {
        PayoutTransaction payoutTransaction = paymentTransactionService.requestManualPayout(paymentId);
        return ResponseEntity.ok(ApiResponse.success("정산 지급 요청이 생성되었습니다.", payoutTransaction));
    }
}
