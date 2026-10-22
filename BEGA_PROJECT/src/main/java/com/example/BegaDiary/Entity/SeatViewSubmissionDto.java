package com.example.BegaDiary.Entity;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public record SeatViewSubmissionDto(
        Long id,
        String storagePath,
        @Schema(nullable = true)
        String photoUrl,
        String sourceType,
        String moderationStatus,
        String aiSuggestedLabel,
        Double aiConfidence,
        String stadium,
        String section,
        String block,
        String seatRow,
        String seatNumber,
        Integer rating,
        String comment,
        List<String> tags) {
}
