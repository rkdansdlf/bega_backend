package com.example.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 관리자용 메이트 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminMateDto {
    private Long id;
    private String teamId;
    private String title;
    private String stadium;
    private LocalDate gameDate;
    private Integer currentMembers;
    private Integer maxMembers;
    private String status;  // "pending", "matched", "selling" 등
    private LocalDateTime createdAt;
    private String hostName;
    private String homeTeam;
    private String awayTeam;
    private String section;
}
