package com.example.BegaDiary.Entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "game")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BegaGame {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "game_id", unique = true, nullable = false, length = 20)
    private String gameId;
    
    @Column(name = "game_date")
    private LocalDate gameDate;
    
    @Column(name = "stadium", length = 50)
    private String stadium;
    
    @Column(name = "home_team", length = 20)
    private String homeTeam;
    
    @Column(name = "away_team", length = 20)
    private String awayTeam;
    
    @Column(name = "home_score")
    private Integer homeScore;
    
    @Column(name = "away_score")
    private Integer awayScore;
    
    @Column(name = "home_pitcher", length = 30)
    private String homePitcher;
    
    @Column(name = "away_pitcher", length = 30)
    private String awayPitcher;
    
    @Column(name = "winning_team", length = 20)
    private String winningTeam;
    
    @Column(name = "winning_score")
    private Integer winningScore;
    
    @Column(name = "season_id")
    private Integer seasonId;
    
    // 스코어 문자열 반환 (5-3 형식)
    public String getScoreString() {
        if (homeScore != null && awayScore != null) {
            return homeScore + "-" + awayScore;
        }
        return null;
    }
}