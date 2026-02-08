package com.example.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 권한 변경 응답 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleChangeResponseDto {
    private Long userId;
    private String email;
    private String name;
    private String previousRole;
    private String newRole;
    private Instant changedAt;
}
