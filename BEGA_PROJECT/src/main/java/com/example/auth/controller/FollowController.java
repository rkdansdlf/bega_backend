package com.example.auth.controller;

import com.example.auth.dto.FollowCountResponse;
import com.example.auth.dto.FollowToggleResponse;
import com.example.auth.dto.UserFollowSummaryDto;
import com.example.auth.service.FollowService;
import com.example.cheerboard.config.CurrentUser;
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
    private final CurrentUser currentUser;

    @RateLimit(limit = 30, window = 60)
    @PostMapping("/profile/{handle}/follow")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FollowToggleResponse> toggleFollowByHandle(@PathVariable String handle) {
        return ResponseEntity.ok(followService.toggleFollowByHandle(handle));
    }

    @PutMapping("/profile/{handle}/follow/notify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FollowToggleResponse> updateNotifySettingByHandle(
            @PathVariable String handle,
            @RequestParam boolean notify) {
        return ResponseEntity.ok(followService.updateNotifySettingByHandle(handle, notify));
    }

    @GetMapping("/profile/{handle}/follow-counts")
    public ResponseEntity<FollowCountResponse> getPublicFollowCounts(@PathVariable String handle) {
        return ResponseEntity.ok(followService.getPublicFollowCounts(handle));
    }

    @GetMapping("/me/follow-counts")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FollowCountResponse> getMyFollowCounts() {
        return ResponseEntity.ok(followService.getFollowCounts(currentUser.get().getId()));
    }

    @GetMapping("/me/followers")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<UserFollowSummaryDto>> getMyFollowers(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(followService.getFollowers(currentUser.get().getId(), pageable));
    }

    @GetMapping("/me/following")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<UserFollowSummaryDto>> getMyFollowing(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(followService.getFollowing(currentUser.get().getId(), pageable));
    }

    @GetMapping("/profile/{handle}/followers")
    public ResponseEntity<Page<UserFollowSummaryDto>> getPublicFollowers(
            @PathVariable String handle,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(followService.getPublicFollowers(handle, pageable));
    }

    @GetMapping("/profile/{handle}/following")
    public ResponseEntity<Page<UserFollowSummaryDto>> getPublicFollowing(
            @PathVariable String handle,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(followService.getPublicFollowing(handle, pageable));
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
