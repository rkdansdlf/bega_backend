package com.example.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 팔로우 카운트 조회 응답 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FollowCountResponse {
    private long followerCount;
    private long followingCount;
    private boolean isFollowedByMe;
    private boolean notifyNewPosts;  // 내가 이 유저를 팔로우 중일 때 알림 설정 상태
}
