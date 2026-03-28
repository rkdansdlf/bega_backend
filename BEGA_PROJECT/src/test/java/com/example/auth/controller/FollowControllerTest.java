package com.example.auth.controller;

import com.example.auth.dto.FollowCountResponse;
import com.example.auth.dto.FollowToggleResponse;
import com.example.auth.dto.UserFollowSummaryDto;
import com.example.auth.service.FollowService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowControllerTest {

    @Mock
    private FollowService followService;

    @InjectMocks
    private FollowController controller;

    private final Pageable pageable = PageRequest.of(0, 20);

    @Test
    @DisplayName("팔로우 토글 시 응답을 반환한다")
    void toggleFollowByHandle_returnsResponse() {
        FollowToggleResponse resp = FollowToggleResponse.builder().following(true).build();
        when(followService.toggleFollowByHandle("testuser")).thenReturn(resp);

        ResponseEntity<FollowToggleResponse> result = controller.toggleFollowByHandle("testuser");

        assertThat(result.getBody().isFollowing()).isTrue();
    }

    @Test
    @DisplayName("알림 설정을 업데이트한다")
    void updateNotifySettingByHandle_returnsResponse() {
        FollowToggleResponse resp = FollowToggleResponse.builder().build();
        when(followService.updateNotifySettingByHandle("testuser", true)).thenReturn(resp);

        ResponseEntity<FollowToggleResponse> result = controller.updateNotifySettingByHandle("testuser", true);

        assertThat(result.getBody()).isEqualTo(resp);
    }

    @Test
    @DisplayName("팔로우 카운트를 조회한다")
    void getPublicFollowCounts_returnsResponse() {
        FollowCountResponse resp = FollowCountResponse.builder().followerCount(10).followingCount(5).build();
        when(followService.getPublicFollowCounts("testuser")).thenReturn(resp);

        ResponseEntity<FollowCountResponse> result = controller.getPublicFollowCounts("testuser");

        assertThat(result.getBody()).isEqualTo(resp);
    }

    @Test
    @DisplayName("팔로워 목록을 조회한다")
    void getPublicFollowers_returnsPage() {
        Page<UserFollowSummaryDto> page = new PageImpl<>(List.of());
        when(followService.getPublicFollowers("testuser", pageable)).thenReturn(page);

        ResponseEntity<Page<UserFollowSummaryDto>> result = controller.getPublicFollowers("testuser", pageable);

        assertThat(result.getBody()).isEqualTo(page);
    }

    @Test
    @DisplayName("팔로잉 목록을 조회한다")
    void getPublicFollowing_returnsPage() {
        Page<UserFollowSummaryDto> page = new PageImpl<>(List.of());
        when(followService.getPublicFollowing("testuser", pageable)).thenReturn(page);

        ResponseEntity<Page<UserFollowSummaryDto>> result = controller.getPublicFollowing("testuser", pageable);

        assertThat(result.getBody()).isEqualTo(page);
    }

    @Test
    @DisplayName("팔로워를 삭제한다")
    void removeFollower_returns204() {
        ResponseEntity<Void> result = controller.removeFollower(99L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(followService).removeFollower(99L);
    }
}
