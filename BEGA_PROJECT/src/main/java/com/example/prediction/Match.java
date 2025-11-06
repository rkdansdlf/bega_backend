package com.example.prediction;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "game")
@Getter
@NoArgsConstructor
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

	public Match(String gameId, LocalDate gameDate, String homeTeam, String awayTeam, String stadium) {
		this.gameId = gameId;
		this.gameDate = gameDate;
		this.homeTeam = homeTeam;
		this.awayTeam = awayTeam;
		this.stadium = stadium;
	}

	
}
