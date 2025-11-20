
package com.example.prediction;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "predictions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Prediction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "game_id", nullable = false)
    private String gameId;
    
    @Column(name = "user_id")
    private Long userId;
    
    @Column(name = "voted_team", nullable = false)
    private String votedTeam;  // "home" 또는 "away"
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Builder
    public Prediction(String gameId, Long userId, String votedTeam) {
        this.gameId = gameId;
        this.userId = userId;
        this.votedTeam = votedTeam;
        this.createdAt = LocalDateTime.now();
    }
    
 
    public void updateVotedTeam(String newVotedTeam) {
        this.votedTeam = newVotedTeam;
        this.createdAt = LocalDateTime.now();
    }
}
