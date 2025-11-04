package com.example.demo.prediction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "teams")
@Getter
@NoArgsConstructor
public class Team {

	@Id
	@Column(name = "team_id")
	private String teamId;
	
	@Column(name = "team_name")
	private String teamName;
	
	@Column(name = "team_short_name")
	private String teamShortName;

	@Builder
	public Team(String teamId, String teamName, String teamShortName) {
		this.teamId = teamId;
		this.teamName = teamName;
		this.teamShortName = teamShortName;
	}
	
	
	
	
}
