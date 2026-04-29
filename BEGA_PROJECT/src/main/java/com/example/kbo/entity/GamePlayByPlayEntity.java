package com.example.kbo.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "game_play_by_play")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GamePlayByPlayEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "game_id", nullable = false, length = 20)
    private String gameId;

    @Column(name = "inning")
    private Integer inning;

    @Column(name = "inning_half", length = 10)
    private String inningHalf;

    @Column(name = "pitcher_name", length = 50)
    private String pitcherName;

    @Column(name = "batter_name", length = 50)
    private String batterName;

    @Column(name = "play_description", length = 1000)
    private String playDescription;

    @Column(name = "event_type", length = 50)
    private String eventType;

    @Column(name = "result", length = 100)
    private String result;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
