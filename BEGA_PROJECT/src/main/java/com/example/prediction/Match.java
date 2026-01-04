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
    
    // --- 확장된 경기 정보 ---

    // 홈 선발 투수
    @Column(name = "home_pitcher_name")
    private String homePitcherName;
    @Column(name = "home_pitcher_era")
    private String homePitcherEra;
    @Column(name = "home_pitcher_win")
    private Integer homePitcherWin;
    @Column(name = "home_pitcher_loss")
    private Integer homePitcherLoss;
    @Column(name = "home_pitcher_img")
    private String homePitcherImg;

    // 원정 선발 투수
    @Column(name = "away_pitcher_name")
    private String awayPitcherName;
    @Column(name = "away_pitcher_era")
    private String awayPitcherEra;
    @Column(name = "away_pitcher_win")
    private Integer awayPitcherWin;
    @Column(name = "away_pitcher_loss")
    private Integer awayPitcherLoss;
    @Column(name = "away_pitcher_img")
    private String awayPitcherImg;

    // AI 요약
    @Column(name = "ai_summary", length = 1000)
    private String aiSummary;

    // 승리 확률
    @Column(name = "win_prob_home")
    private Double winProbHome;
    @Column(name = "win_prob_away")
    private Double winProbAway;
    
    @Builder
    public Match(String gameId, LocalDate gameDate, String homeTeam, 
                 String awayTeam, String stadium, Integer homeScore, 
                 Integer awayScore, String winningTeam, Boolean isDummy,
                 String homePitcherName, String homePitcherEra, Integer homePitcherWin, Integer homePitcherLoss, String homePitcherImg,
                 String awayPitcherName, String awayPitcherEra, Integer awayPitcherWin, Integer awayPitcherLoss, String awayPitcherImg,
                 String aiSummary, Double winProbHome, Double winProbAway) {
        this.gameId = gameId;
        this.gameDate = gameDate;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.stadium = stadium;
        this.homeScore = homeScore;
        this.awayScore = awayScore;
        this.winningTeam = winningTeam;
        this.isDummy = isDummy != null ? isDummy : false;
        
        this.homePitcherName = homePitcherName;
        this.homePitcherEra = homePitcherEra;
        this.homePitcherWin = homePitcherWin;
        this.homePitcherLoss = homePitcherLoss;
        this.homePitcherImg = homePitcherImg;
        
        this.awayPitcherName = awayPitcherName;
        this.awayPitcherEra = awayPitcherEra;
        this.awayPitcherWin = awayPitcherWin;
        this.awayPitcherLoss = awayPitcherLoss;
        this.awayPitcherImg = awayPitcherImg;
        
        this.aiSummary = aiSummary;
        this.winProbHome = winProbHome;
        this.winProbAway = winProbAway;
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
    
    
}