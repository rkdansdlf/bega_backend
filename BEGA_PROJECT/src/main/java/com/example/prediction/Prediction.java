package com.example.demo.prediction;

import java.time.LocalDateTime;

import org.hibernate.annotations.Collate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "predictions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Prediction {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@Column(name = "game_id" , nullable = false)
	private String gameId;
	
	@Column(name = "user_id")
	private Long userId;
	
	@Column(name = "voted_team")
	private String votedTeam;
	
	@Column(name = "created_at")
	private LocalDateTime createdAt;

	@Builder
	public Prediction(String gameId, Long userId, String votedTeam) {
		this.gameId = gameId;
		this.userId = userId;
		this.votedTeam = votedTeam;
		this.createdAt = LocalDateTime.now();
	}
	
	

}
