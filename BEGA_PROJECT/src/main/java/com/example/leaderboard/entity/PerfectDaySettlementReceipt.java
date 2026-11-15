package com.example.leaderboard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "perfect_day_settlement_receipts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_perfect_day_receipt_user_date",
                columnNames = {"user_id", "game_date"}))
@Getter
@NoArgsConstructor
public class PerfectDaySettlementReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "game_date", nullable = false)
    private LocalDate gameDate;

    @Column(name = "games_won", nullable = false)
    private Integer gamesWon;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public PerfectDaySettlementReceipt(Long userId, LocalDate gameDate, Integer gamesWon) {
        this.userId = userId;
        this.gameDate = gameDate;
        this.gamesWon = gamesWon;
        this.createdAt = LocalDateTime.now();
    }
}
