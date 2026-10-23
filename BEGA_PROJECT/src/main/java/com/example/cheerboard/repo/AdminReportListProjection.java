package com.example.cheerboard.repo;

import com.example.cheerboard.domain.CheerPostReport;
import com.example.cheerboard.domain.ReportReason;

import java.time.LocalDateTime;

public interface AdminReportListProjection {

    Long getId();

    Long getPostId();

    String getPostContent();

    Long getReporterId();

    String getReporterHandle();

    ReportReason getReason();

    String getDescription();

    CheerPostReport.ReportStatus getStatus();

    CheerPostReport.AdminAction getAdminAction();

    String getAdminMemo();

    Long getHandledBy();

    LocalDateTime getHandledAt();

    String getEvidenceUrl();

    String getRequestedAction();

    CheerPostReport.AppealStatus getAppealStatus();

    String getAppealReason();

    Integer getAppealCount();

    LocalDateTime getCreatedAt();
}
