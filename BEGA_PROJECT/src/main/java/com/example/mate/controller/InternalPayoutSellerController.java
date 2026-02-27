package com.example.mate.controller;

import com.example.common.dto.ApiResponse;
import com.example.mate.dto.SellerPayoutProfileDTO;
import com.example.mate.entity.SellerPayoutProfile;
import com.example.mate.service.SellerPayoutProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/payout/sellers")
@RequiredArgsConstructor
public class InternalPayoutSellerController {

    private final SellerPayoutProfileService sellerPayoutProfileService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse> upsertSellerProfile(
            @RequestBody SellerPayoutProfileDTO.UpsertRequest request) {
        SellerPayoutProfile profile = sellerPayoutProfileService.upsert(request);
        return ResponseEntity.ok(ApiResponse.success(
                "판매자 정산 매핑이 저장되었습니다.",
                SellerPayoutProfileDTO.Response.from(profile)));
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse> getSellerProfile(
            @PathVariable Long userId,
            @RequestParam(name = "provider", defaultValue = "TOSS") String provider) {
        return sellerPayoutProfileService.findByUserIdAndProvider(userId, provider)
                .map(profile -> ResponseEntity.ok(ApiResponse.success(
                        "판매자 정산 매핑 조회 성공",
                        SellerPayoutProfileDTO.Response.from(profile))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("판매자 정산 매핑을 찾을 수 없습니다.")));
    }
}

