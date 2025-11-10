package com.example.prediction;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "vote_final_results") // 새로운 테이블 생성
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VoteFinalResult {
	// PK를 별도로 두지 않고, gameId를 PK로 사용하거나 
    // gameId를 PK 겸 FK로 사용하여 1:1 관계를 명확히 할 수 있습니다.
    @Id
    @Column(name = "game_id", nullable = false)
    private String gameId; 
    
    // 최종 투표 수 (또는 백분율을 저장)
    @Column(name = "final_votes_a", nullable = false)
    private int finalVotesA;

    @Column(name = "final_votes_b", nullable = false)
    private int finalVotesB;
    
    // 최종 승리팀 (예: "TEAM_A", "TEAM_B", "DRAW")
    @Column(name = "final_winner")
    private String finalWinner; 
    
    // 결과 저장 시점
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    
    @Builder
    public VoteFinalResult(String gameId, int finalVotesA, int finalVotesB, String finalWinner) {
        this.gameId = gameId;
        this.finalVotesA = finalVotesA;
        this.finalVotesB = finalVotesB;
        this.finalWinner = finalWinner;
        this.updatedAt = LocalDateTime.now();
    }
}

