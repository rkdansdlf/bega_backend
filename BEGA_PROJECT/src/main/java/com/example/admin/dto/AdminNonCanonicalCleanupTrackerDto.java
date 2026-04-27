package com.example.admin.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AdminNonCanonicalCleanupTrackerDto(
        LocalDate startDate,
        LocalDate endDate,
        String ticketUrl,
        String assignee,
        String status,
        String note,
        LocalDateTime updatedAt,
        List<String> gameIds
) {
}
