package com.example.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 관리자용 게시글 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminPostDto {
    private Long id;
    private String team;
    private String title;
    private String author;
    private Instant createdAt;
    private Integer likeCount;
    private Integer commentCount;
    private Integer views;
    private Boolean isHot;
}
