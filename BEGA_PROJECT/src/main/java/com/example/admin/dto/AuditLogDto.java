package com.example.admin.dto;

import com.example.admin.entity.AuditLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 감사 로그 응답 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogDto {
    private Long id;
    private Long adminId;
    private String adminEmail;
    private String adminName;
    private Long targetUserId;
    private String targetUserEmail;
    private String targetUserName;
    private String action;
    private String actionDescription;
    private String oldValue;
    private String newValue;
    private String description;
    private LocalDateTime createdAt;

    /**
     * Entity -> DTO 변환 (기본 정보만, 사용자 정보 없이)
     */
    public static AuditLogDto from(AuditLog entity) {
        return AuditLogDto.builder()
                .id(entity.getId())
                .adminId(entity.getAdminId())
                .targetUserId(entity.getTargetUserId())
                .action(entity.getAction().name())
                .actionDescription(entity.getAction().getDescription())
                .oldValue(entity.getOldValue())
                .newValue(entity.getNewValue())
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
