package com.example.BegaDiary.Entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameResponseDto {
    private Long id;
    private String homeTeam;
    private String awayTeam;
    private String stadium;
    private String score;  // "5-3" 형식
    private String date;
}
