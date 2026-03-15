package com.example.common.clienterror.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ClientErrorDashboardDto(
        OffsetDateTime from,
        OffsetDateTime to,
        String granularity,
        ClientErrorDashboardTotalsDto totals,
        List<ClientErrorTimeSeriesPointDto> timeSeries,
        List<ClientErrorTopFingerprintDto> topFingerprints,
        List<ClientErrorRecentFeedbackDto> recentFeedback,
        List<ClientErrorAlertNotificationDto> recentAlerts) {
}
