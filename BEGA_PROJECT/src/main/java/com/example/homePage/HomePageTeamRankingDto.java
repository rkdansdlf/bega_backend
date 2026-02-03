package com.example.homepage;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomePageTeamRankingDto {
    private Integer rank;
    private String teamId;
    private String teamName;
    private Integer wins;
    private Integer losses;
    private Integer draws;
    private String winRate;
    private Integer games;
    private Double gamesBehind;
}
