package com.example.prediction;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "game")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Match {
    
    @Id
    @Column(name = "game_id")
    private String gameId;  
    
    @Column(name = "game_date")
    private LocalDate gameDate;
    
    @Column(name = "home_team")
    private String homeTeam;  
    
    @Column(name = "away_team")
    private String awayTeam;  
    
    @Column(name = "stadium")
    private String stadium;  
    
    @Column(name = "home_score")
    private Integer homeScore;

    @Column(name = "away_score")
    private Integer awayScore;

    @Column(name = "winning_team")
    private String winningTeam;
    
    @Column(name = "is_dummy")
    private Boolean isDummy;
    
    @Builder
    public Match(String gameId, LocalDate gameDate, String homeTeam, 
                 String awayTeam, String stadium, Integer homeScore, 
                 Integer awayScore, String winningTeam, Boolean isDummy) {
        this.gameId = gameId;
        this.gameDate = gameDate;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.stadium = stadium;
        this.homeScore = homeScore;
        this.awayScore = awayScore;
        this.winningTeam = winningTeam;
        this.isDummy = isDummy != null ? isDummy : false;
    }
    
    // 승리팀 계산 (스코어가 있는 경우만 결과 계산)
    public String getWinner() {
        if (homeScore == null || awayScore == null) {
            return null; // 스코어가 없으면 경기 미종료 또는 데이터 없음
        }
        if (homeScore.equals(awayScore)) {
            return "draw";
        }
        return homeScore > awayScore ? "home" : "away";
    }
    
    // 경기가 종료 여부 (스코어 유무로 판단)
    public boolean isFinished() {
        return homeScore != null && awayScore != null;
    }
    
    // 더미 데이터 날짜 변경 (스케줄러 전용)
    public void setGameDate(LocalDate newDate) {
        if (!this.isDummy) {
            throw new IllegalStateException("실제 경기 데이터의 날짜는 변경할 수 없습니다.");
        }
        this.gameDate = newDate;
    }
    
}