package com.example.common.clienterror.dto;

public record ClientErrorDashboardTotalsDto(
        long api,
        long runtime,
        long feedback,
        long uniqueFingerprints,
        long affectedRoutes) {
}
