package com.example.admin.dto;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminSeatViewDto {
    private Long id;
    @Schema(nullable = true)
    private Long diaryId;
    private Long userId;
    @Schema(nullable = true)
    private String photoUrl;
    private String storagePath;
    private String sourceType;
    private String aiSuggestedLabel;
    private Double aiConfidence;
    private String aiReason;
    private boolean userSelected;
    private String moderationStatus;
    private String adminLabel;
    private String adminMemo;
    private Long reviewedBy;
    private String reviewedAt;
    private boolean rewardGranted;
    private String stadium;
    private String section;
    private String block;
    private String seatRow;
    private String seatNumber;
    private String diaryDate;
    private boolean ticketVerified;
    private String ticketVerifiedAt;
    @Schema(nullable = true)
    private Integer rating;
    @Schema(nullable = true)
    private String comment;
    private List<String> tags;
}
