package com.example.BegaDiary.Entity;

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
}
