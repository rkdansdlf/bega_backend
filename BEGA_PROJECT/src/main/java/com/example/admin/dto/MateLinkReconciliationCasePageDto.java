package com.example.admin.dto;

import java.util.List;

public record MateLinkReconciliationCasePageDto(
        List<MateLinkReconciliationCaseDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
