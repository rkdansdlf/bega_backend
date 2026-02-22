package com.example.auth.controller;

import com.example.auth.dto.BlockToggleResponse;
import com.example.auth.dto.UserFollowSummaryDto;
import com.example.auth.service.BlockService;
import com.example.common.ratelimit.RateLimit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    /**
     * 차단 토글 (차단/차단해제)
     */
    @RateLimit(limit = 30, window = 60) // 1분에 최대 30번
    @PostMapping("/{userId}/block")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BlockToggleResponse> toggleBlock(@PathVariable Long userId) {
        return ResponseEntity.ok(blockService.toggleBlock(userId));
    }

    /**
     * 내가 차단한 유저 목록 조회
     */
    @GetMapping("/me/blocked")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<UserFollowSummaryDto>> getBlockedUsers(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(blockService.getBlockedUsers(pageable));
    }
}
