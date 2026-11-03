package com.example.admin.dto;

import jakarta.validation.constraints.Size;

public record AdminReportAppealReq(
        @Size(max = 1200, message = "이의제기 사유는 1200자 이하여야 합니다.")
        String appealReason
) {
}
