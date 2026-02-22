package com.example.cheerboard.dto;

import com.example.cheerboard.domain.ReportReason;

public record ReportRequest(
        ReportReason reason,
        String description,
        String sourceUrl,
        Boolean hasRightEvidence,
        String license,
        String ownerContact,
        String requestedReason,
        String requestedAction,
        String evidenceUrl) {
        public ReportRequest(ReportReason reason, String description) {
                this(reason, description, null, null, null, null, null, null, null);
        }
}
