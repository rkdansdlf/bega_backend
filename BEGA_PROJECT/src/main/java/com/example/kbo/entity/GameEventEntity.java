package com.example.kbo.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
@Table(name = "game_events")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "game_id", nullable = false)
    private String gameId;

    @Column(name = "event_seq", nullable = false)
    private Integer eventSeq;

    @Column(name = "inning")
    private Integer inning;

    @Column(name = "inning_half")
    private String inningHalf;

    @Column(name = "outs")
    private Integer outs;

    @Column(name = "batter_id")
    private Integer batterId;

    @Column(name = "batter_name")
    private String batterName;

    @Column(name = "pitcher_id")
    private Integer pitcherId;

    @Column(name = "pitcher_name")
    private String pitcherName;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(name = "description")
    private String description;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "result_code")
    private String resultCode;

    @Column(name = "rbi")
    private Integer rbi;

    @Column(name = "bases_before")
    private String basesBefore;

    @Column(name = "bases_after")
    private String basesAfter;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "wpa")
    private Double wpa;

    @Column(name = "win_expectancy_before")
    private Double winExpectancyBefore;

    @Column(name = "win_expectancy_after")
    private Double winExpectancyAfter;

    @Column(name = "score_diff")
    private Integer scoreDiff;

    @Column(name = "base_state")
    private Integer baseState;

    @Column(name = "home_score")
    private Integer homeScore;

    @Column(name = "away_score")
    private Integer awayScore;
}
