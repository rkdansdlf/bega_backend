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

    @RateLimit(limit = 30, window = 60)
    @PostMapping("/profile/{handle}/block")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BlockToggleResponse> toggleBlockByHandle(@PathVariable String handle) {
        return ResponseEntity.ok(blockService.toggleBlockByHandle(handle));
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
