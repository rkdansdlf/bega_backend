package com.example.homePage;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OffseasonMovementDto {
    private Long id;
    private String date;
    private String section;
    private String team;
    private String player;
    private String remarks;

    // Derived fields
    private boolean isBigEvent;
    private Long estimatedAmount; // In 10,000 KRW unit (man-won) or just simplified for sorting
    private String displayAmount; // e.g. "4년 60억"
}
