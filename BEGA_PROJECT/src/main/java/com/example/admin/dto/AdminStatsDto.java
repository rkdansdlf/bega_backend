package com.example.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관리자 대시보드 통계 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminStatsDto {
    private Long totalUsers;    // 총 유저 수
    private Long totalPosts;    // 총 게시글 수
    private Long totalMates;    // 총 메이트 수
}
