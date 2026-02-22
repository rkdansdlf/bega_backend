package com.example.admin.dto;

import com.example.cheerboard.domain.CheerPostReport;

public record AdminReportActionReq(
        CheerPostReport.AdminAction action,
        String adminMemo,
        String visibleUntil
) {
}
