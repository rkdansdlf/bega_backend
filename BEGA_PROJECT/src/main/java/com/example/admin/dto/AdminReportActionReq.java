package com.example.admin.dto;

import com.example.cheerboard.domain.CheerPostReport;
import jakarta.validation.constraints.Size;

public record AdminReportActionReq(
        CheerPostReport.AdminAction action,
        @Size(max = 1000, message = "관리자 메모는 1000자 이하여야 합니다.")
        String adminMemo,
        String visibleUntil
) {
}
