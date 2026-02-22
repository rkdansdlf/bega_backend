package com.example.leaderboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 특정 사용자 랭킹 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRankDto {
    private Long rank;
    private Long score;
    private Integer level;
}
