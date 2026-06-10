package com.example.prediction;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

import lombok.Getter;

@Getter
public class MatchRangePageResponseDto {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private final List<MatchDto> content;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private final int page;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private final int size;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private final long totalElements;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private final int totalPages;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean hasNext;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private final boolean hasPrevious;

    public MatchRangePageResponseDto(
            List<MatchDto> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext,
            boolean hasPrevious
    ) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.hasNext = hasNext;
        this.hasPrevious = hasPrevious;
    }
}
