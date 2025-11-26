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
    
    public static MatchDto fromEntity(Match match) {
        LocalDate displayDate = match.getGameDate();
        
        // 더미 데이터면 항상 내일 날짜로 표시
        if (Boolean.TRUE.equals(match.getIsDummy())) {
            displayDate = LocalDate.now().plusDays(1);
        }

  
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
                .build();
    }
}
