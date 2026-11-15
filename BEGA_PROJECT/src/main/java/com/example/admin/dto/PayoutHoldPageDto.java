package com.example.admin.dto;

import java.util.List;

public record PayoutHoldPageDto(
        List<PayoutHoldDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
