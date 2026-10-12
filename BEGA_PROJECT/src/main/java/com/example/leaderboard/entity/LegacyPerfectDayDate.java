package com.example.leaderboard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "legacy_perfect_day_dates")
@Getter
@NoArgsConstructor
public class LegacyPerfectDayDate {

    @Id
    @Column(name = "game_date", nullable = false)
    private LocalDate gameDate;

    public LegacyPerfectDayDate(LocalDate gameDate) {
        this.gameDate = gameDate;
    }
}
