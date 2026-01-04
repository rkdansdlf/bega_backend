package com.example.prediction;

import java.time.LocalDate;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Tolerate;

@Getter
@Builder(toBuilder = true)
public class MatchDto {
    private String gameId;
    private LocalDate gameDate;
    private String homeTeam;
    private String awayTeam;
    private String stadium;
    private Integer homeScore;
    private Integer awayScore;
    private String winner;
    private Boolean isDummy;
    
    // New Fields
    private PitcherDto homePitcher;
    private PitcherDto awayPitcher;
    private String aiSummary;
    private WinProbabilityDto winProbability;
    
    @Getter
    @Builder
    public static class PitcherDto {
        private String name;
        private String era;
        private Integer win;
        private Integer loss;
        private String imgUrl;
    }

    @Getter
    @Builder
    public static class WinProbabilityDto {
        private Double home;
        private Double away;
    }
    
    public static MatchDto fromEntity(Match match) {
        LocalDate displayDate = match.getGameDate();
        
        // 더미 데이터면 항상 내일 날짜로 표시
        if (Boolean.TRUE.equals(match.getIsDummy())) {
            displayDate = LocalDate.now().plusDays(1);
        }

        // Construct DTO
        return MatchDto.builder()
                .gameId(match.getGameId())
                .gameDate(displayDate)
                .homeTeam(match.getHomeTeam())
                .awayTeam(match.getAwayTeam())
                .stadium(match.getStadium())
                .homeScore(match.getHomeScore())
                .awayScore(match.getAwayScore())
                .winner(match.getWinner())
                .isDummy(match.getIsDummy())
                // Map new fields from Entity
                .homePitcher(match.getHomePitcherName() != null ? PitcherDto.builder()
                        .name(match.getHomePitcherName())
                        .era(match.getHomePitcherEra())
                        .win(match.getHomePitcherWin())
                        .loss(match.getHomePitcherLoss())
                        .imgUrl(match.getHomePitcherImg())
                        .build() : null)
                .awayPitcher(match.getAwayPitcherName() != null ? PitcherDto.builder()
                        .name(match.getAwayPitcherName())
                        .era(match.getAwayPitcherEra())
                        .win(match.getAwayPitcherWin())
                        .loss(match.getAwayPitcherLoss())
                        .imgUrl(match.getAwayPitcherImg())
                        .build() : null)
                .aiSummary(match.getAiSummary())
                .winProbability((match.getWinProbHome() != null && match.getWinProbAway() != null) ? 
                        WinProbabilityDto.builder()
                        .home(match.getWinProbHome())
                        .away(match.getWinProbAway())
                        .build() : null)
                .build();
    }
}
