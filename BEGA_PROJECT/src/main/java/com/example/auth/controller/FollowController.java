package com.example.auth.controller;

import com.example.auth.dto.FollowCountResponse;
import com.example.auth.dto.FollowToggleResponse;
import com.example.auth.dto.UserFollowSummaryDto;
import com.example.auth.service.FollowService;
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
public class FollowController {

    private final FollowService followService;

    /**
     * 팔로우 토글 (팔로우/언팔로우)
     */
    @RateLimit(limit = 30, window = 60) // 1분에 최대 30번
    @PostMapping("/{userId}/follow")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FollowToggleResponse> toggleFollow(@PathVariable Long userId) {
        return ResponseEntity.ok(followService.toggleFollow(userId));
    }

    /**
     * 알림 설정 변경
     */
    @PutMapping("/{userId}/follow/notify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FollowToggleResponse> updateNotifySetting(
            @PathVariable Long userId,
            @RequestParam boolean notify) {
        return ResponseEntity.ok(followService.updateNotifySetting(userId, notify));
    }

    /**
     * 팔로우 카운트 및 상태 조회
     */
    @GetMapping("/{userId}/follow-counts")
    public ResponseEntity<FollowCountResponse> getFollowCounts(@PathVariable Long userId) {
        return ResponseEntity.ok(followService.getFollowCounts(userId));
    }

    /**
     * 팔로워 목록 조회
     */
    @GetMapping("/{userId}/followers")
    public ResponseEntity<Page<UserFollowSummaryDto>> getFollowers(
            @PathVariable Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(followService.getFollowers(userId, pageable));
    }

    /**
     * 팔로잉 목록 조회
     */
    @GetMapping("/{userId}/following")
    public ResponseEntity<Page<UserFollowSummaryDto>> getFollowing(
            @PathVariable Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(followService.getFollowing(userId, pageable));
    }

    /**
     * 팔로워 삭제 (상대방이 나를 팔로우하는 관계 삭제)
     */
    @DeleteMapping("/me/followers/{followerId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> removeFollower(@PathVariable Long followerId) {
        followService.removeFollower(followerId);
        return ResponseEntity.noContent().build();
    }
}
