package com.example.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class OffseasonMovementAdminDto {
    private Long id;
    private LocalDate movementDate;
    private String section;
    private String teamCode;
    private String playerName;
    private String summary;
    private String details;
    private String contractTerm;
    private String contractValue;
    private String optionDetails;
    private String counterpartyTeam;
    private String counterpartyDetails;
    private String sourceLabel;
    private String sourceUrl;
    private LocalDateTime announcedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
