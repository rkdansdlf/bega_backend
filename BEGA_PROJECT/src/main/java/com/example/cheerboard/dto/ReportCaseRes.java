package com.example.cheerboard.dto;

import java.time.LocalDateTime;

public record ReportCaseRes(
        Long caseId,
        String reportStatus,
        LocalDateTime handledAt,
        String nextAction,
        String adminMessage) {
}
