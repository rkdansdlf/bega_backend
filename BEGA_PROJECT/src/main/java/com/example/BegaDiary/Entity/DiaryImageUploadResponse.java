package com.example.BegaDiary.Entity;

import java.util.List;

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
public class DiaryImageUploadResponse {
    private String message;
    private Long diaryId;
    private List<String> photos;
    private List<SeatViewCandidateDto> candidates;
}
