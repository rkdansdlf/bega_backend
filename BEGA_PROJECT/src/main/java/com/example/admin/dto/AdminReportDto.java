package com.example.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminReportDto {
    private Long id;
    private Long postId;
    private String postPreview;
    private Long reporterId;
    private String reporterHandle;
    private String reason;
    private String description;
    private String status;
    private String adminAction;
    private String adminMemo;
    private Long handledBy;
    private LocalDateTime handledAt;
    private String evidenceUrl;
    private String requestedAction;
    private String appealStatus;
    private String appealReason;
    private Integer appealCount;
    private LocalDateTime createdAt;
}
