package com.example.cheerboard.dto;

import com.example.cheerboard.domain.ReportReason;

public record ReportRequest(
        ReportReason reason,
        String description) {
}
