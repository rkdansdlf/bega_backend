package com.example.homePage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OffseasonMetaDto {

    private List<AwardDto> awards;
    private List<PostSeasonResultDto> postSeasonResults;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AwardDto {
        private String award;
        private String playerName;
        private String team;
        private String stats;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostSeasonResultDto {
        private String title;
        private String result;
        private String detail;
    }
}
