package com.example.common.service.port;

public record ContentModerationDecision(
        String category,
        String reason,
        String action,
        String decisionSource,
        String riskLevel) {
}
