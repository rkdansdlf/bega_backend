package com.example.admin.dto;

import jakarta.validation.constraints.NotNull;

public record AdminReportAppealDecisionReq(
        @NotNull(message = "이의제기 결정은 필수입니다.")
        AdminReportAppealDecision decision) {
}
