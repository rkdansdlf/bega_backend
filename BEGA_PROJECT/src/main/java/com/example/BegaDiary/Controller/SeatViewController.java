package com.example.BegaDiary.Controller;

import com.example.BegaDiary.Entity.SeatViewSubmissionDto;
import com.example.BegaDiary.Entity.SeatViewSubmissionRequest;
import com.example.BegaDiary.Entity.SeatViewPreviewDto;
import com.example.BegaDiary.Service.SeatViewService;
import com.example.common.dto.ApiResponse;
import com.example.common.web.AuthenticatedUserIds;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seat-views")
@RequiredArgsConstructor
public class SeatViewController {

    private final SeatViewService seatViewService;

    @PreAuthorize("isAuthenticated()")
    @PostMapping
    public ResponseEntity<ApiResponse<SeatViewSubmissionDto>> submit(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SeatViewSubmissionRequest request) {
        SeatViewSubmissionDto result = seatViewService.createDirectSubmission(
                AuthenticatedUserIds.require(userId),
                request);
        return ResponseEntity.ok(ApiResponse.success("시야뷰 등록이 완료되었습니다.", result));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{seatViewId}/preview")
    public ResponseEntity<ApiResponse<SeatViewPreviewDto>> getOwnerPreview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long seatViewId) {
        SeatViewPreviewDto result = new SeatViewPreviewDto(
                seatViewService.getOwnerPreviewUrl(AuthenticatedUserIds.require(userId), seatViewId));
        return ResponseEntity.ok(ApiResponse.success("시야뷰 미리보기 URL 조회에 성공했습니다.", result));
    }
}
