package com.example.admin.dto;

import java.util.List;

public record PaymentIntentReconciliationPageDto(
        List<PaymentIntentReconciliationReviewDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
