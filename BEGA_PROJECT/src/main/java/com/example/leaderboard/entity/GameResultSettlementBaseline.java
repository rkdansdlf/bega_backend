package com.example.leaderboard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "game_result_settlement_baseline")
@Getter
@NoArgsConstructor
public class GameResultSettlementBaseline {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(name = "cutoff_prediction_id", nullable = false)
    private Long cutoffPredictionId;

    @Column(name = "last_prediction_id", nullable = false)
    private Long lastPredictionId;

    @Column(name = "is_complete", nullable = false)
    private Boolean complete;

    @Column(name = "pending_game_cursor", length = 255)
    private String pendingGameCursor;

    public GameResultSettlementBaseline(
            Long id,
            Long cutoffPredictionId,
            Long lastPredictionId,
            Boolean complete) {
        this(id, cutoffPredictionId, lastPredictionId, complete, null);
    }

    public GameResultSettlementBaseline(
            Long id,
            Long cutoffPredictionId,
            Long lastPredictionId,
            Boolean complete,
            String pendingGameCursor) {
        this.id = id;
        this.cutoffPredictionId = cutoffPredictionId;
        this.lastPredictionId = lastPredictionId;
        this.complete = complete;
        this.pendingGameCursor = pendingGameCursor;
    }

    public boolean isComplete() {
        return Boolean.TRUE.equals(complete);
    }

    public void advanceTo(Long lastPredictionId, boolean complete) {
        this.lastPredictionId = lastPredictionId;
        this.complete = complete;
    }

    public void advancePendingGameCursor(String pendingGameCursor) {
        this.pendingGameCursor = pendingGameCursor;
    }
}
