package com.example.auth.controller;

import com.example.auth.dto.AccountDeletionRecoveryInfoDto;
import com.example.auth.dto.AccountDeletionRecoveryRequestDto;
import com.example.auth.service.AccountDeletionService;
import com.example.auth.service.AccountSecurityService;
import com.example.common.dto.ApiResponse;
import com.example.common.web.AuthenticatedUserIds;
import com.example.mypage.dto.AccountSecurityEventDto;
import com.example.mypage.dto.TrustedDeviceDto;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<ApiResponse<List<AccountSecurityEventDto>>> getSecurityEvents(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(
                "최근 보안 활동 조회 성공",
                accountSecurityService.getSecurityEvents(AuthenticatedUserIds.require(userId))));
    }

    @GetMapping("/trusted-devices")
    public ResponseEntity<ApiResponse<List<TrustedDeviceDto>>> getTrustedDevices(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success(
                "신뢰 기기 조회 성공",
                accountSecurityService.getTrustedDevices(AuthenticatedUserIds.require(userId))));
    }

    @DeleteMapping("/trusted-devices/{deviceId}")
    public ResponseEntity<ApiResponse<Void>> deleteTrustedDevice(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long deviceId) {
        accountSecurityService.revokeTrustedDevice(AuthenticatedUserIds.require(userId), deviceId);
        return ResponseEntity.ok(ApiResponse.success("신뢰 기기가 해제되었습니다. 현재 로그인 세션은 유지됩니다."));
    }

    @GetMapping("/account/deletion/recovery")
    public ResponseEntity<ApiResponse<AccountDeletionRecoveryInfoDto>> getDeletionRecoveryInfo(@RequestParam("token") String token) {
        AccountDeletionRecoveryInfoDto info = accountDeletionService.getRecoveryInfo(token);
        return ResponseEntity.ok(ApiResponse.success("계정 삭제 복구 정보 조회 성공", info));
    }

    @PostMapping("/account/deletion/recovery")
    public ResponseEntity<ApiResponse<Void>> recoverDeletedAccount(
            @Valid @RequestBody AccountDeletionRecoveryRequestDto request) {
        accountDeletionService.recoverAccount(request.getToken());
        return ResponseEntity.ok(ApiResponse.success("계정 삭제 예약이 취소되었습니다."));
    }
}
