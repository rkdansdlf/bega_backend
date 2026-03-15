package com.example.common.clienterror.dto;

import java.time.OffsetDateTime;

public record ClientErrorTimeSeriesPointDto(
        OffsetDateTime bucketStart,
        long api,
        long runtime,
        long feedback) {
}
