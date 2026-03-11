package com.example.auth.controller;

import com.example.auth.dto.AccountDeletionRecoveryInfoDto;
import com.example.auth.dto.AccountDeletionRecoveryRequestDto;
import com.example.auth.service.AccountDeletionService;
import com.example.auth.service.AccountSecurityService;
import com.example.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AccountSecurityController {

    private final AccountSecurityService accountSecurityService;
    private final AccountDeletionService accountDeletionService;

    @GetMapping("/security-events")
    public ResponseEntity<ApiResponse> getSecurityEvents(@AuthenticationPrincipal Long userId) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("인증이 필요합니다."));
        }

        return ResponseEntity.ok(ApiResponse.success(
                "최근 보안 활동 조회 성공",
                accountSecurityService.getSecurityEvents(userId)));
    }

    @GetMapping("/trusted-devices")
    public ResponseEntity<ApiResponse> getTrustedDevices(@AuthenticationPrincipal Long userId) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("인증이 필요합니다."));
        }

        return ResponseEntity.ok(ApiResponse.success(
                "신뢰 기기 조회 성공",
                accountSecurityService.getTrustedDevices(userId)));
    }

    @DeleteMapping("/trusted-devices/{deviceId}")
    public ResponseEntity<ApiResponse> deleteTrustedDevice(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long deviceId) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("인증이 필요합니다."));
        }

        try {
            accountSecurityService.revokeTrustedDevice(userId, deviceId);
            return ResponseEntity.ok(ApiResponse.success("신뢰 기기가 해제되었습니다."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/account/deletion/recovery")
    public ResponseEntity<ApiResponse> getDeletionRecoveryInfo(@RequestParam("token") String token) {
        try {
            AccountDeletionRecoveryInfoDto info = accountDeletionService.getRecoveryInfo(token);
            return ResponseEntity.ok(ApiResponse.success("계정 삭제 복구 정보 조회 성공", info));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/account/deletion/recovery")
    public ResponseEntity<ApiResponse> recoverDeletedAccount(
            @Valid @RequestBody AccountDeletionRecoveryRequestDto request) {
        try {
            accountDeletionService.recoverAccount(request.getToken());
            return ResponseEntity.ok(ApiResponse.success("계정 삭제 예약이 취소되었습니다."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
}
