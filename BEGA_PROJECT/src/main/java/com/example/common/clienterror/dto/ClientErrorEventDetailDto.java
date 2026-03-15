package com.example.common.clienterror.dto;

import java.util.List;

public record ClientErrorEventDetailDto(
        ClientErrorEventSummaryDto event,
        String stack,
        String componentStack,
        List<ClientErrorRecentFeedbackDto> feedback,
        List<ClientErrorEventSummaryDto> sameFingerprintRecentEvents) {
}
