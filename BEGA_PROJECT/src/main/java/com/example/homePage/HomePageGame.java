package com.example.homePage;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "game")
@Getter
@NoArgsConstructor
public class HomePageGame {
	
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", unique = true, nullable = false)
    private String gameId;

    @Column(name = "game_date")
    private LocalDate gameDate; // 경기 날짜

    private String stadium;
    
    @Column(name = "home_team")
    private String homeTeamId; // 팀 ID
    
    @Column(name = "away_team")
    private String awayTeamId; // 팀 ID
    
    @Column(name = "game_status")
    private String gameStatus; // SCHEDULED, COMPLETED 등

    @Column(name = "home_score")
    private Integer homeScore;

    @Column(name = "away_score")
    private Integer awayScore;
}
