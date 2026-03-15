package com.example.common.clienterror.dto;

import java.util.List;

public record ClientErrorEventPageDto(
        List<ClientErrorEventSummaryDto> content,
        long totalElements,
        int totalPages,
        int size,
        int number,
        boolean last) {
}
