package com.example.BegaDiary.Entity;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeatViewPhotoDto {
    private String photoUrl;
    private String stadium;
    private String section;
    private String block;
    private String diaryDate;
    private Integer rating;
    private String comment;
    private List<String> tags;
}
