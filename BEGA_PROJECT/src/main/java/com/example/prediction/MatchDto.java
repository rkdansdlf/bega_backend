package com.example.prediction;


import java.time.LocalDate;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Tolerate;


 // 과거 경기 결과 및 정보를 담아 클라이언트에 전송하는 DTO
 
@Getter
@Builder(toBuilder = true)
public class MatchDto {
    private String gameId;
    private LocalDate gameDate;
    private String homeTeam;
    private String awayTeam;
    private String stadium;
    
    // 경기 결과
    private Integer homeScore;
    private Integer awayScore;
    private String winner; // Match.getWinner() 값 (home, away, draw, null)
    
    @Tolerate 
    public MatchDto() {}

    /**
     * Match 엔티티를 MatchDto로 변환하는 팩토리 메서드
     * @param match Match 엔티티
     * @return MatchDto 인스턴스
     */
    public static MatchDto fromEntity(Match match) {
        return MatchDto.builder()
                .gameId(match.getGameId())
                .gameDate(match.getGameDate())
                .homeTeam(match.getHomeTeam())
                .awayTeam(match.getAwayTeam())
                .stadium(match.getStadium())
                .homeScore(match.getHomeScore())
                .awayScore(match.getAwayScore())
                .winner(match.getWinner())
                .build();
    }
}
