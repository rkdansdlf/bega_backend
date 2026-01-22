package com.example.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 관리자용 유저 정보 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUserDto {
    private Long id;
    private String email;
    private String name;
    private String favoriteTeam;
    private Instant createdAt;
    private Long postCount; // 작성한 게시글 수
    private String role; // USER, ADMIN
}
