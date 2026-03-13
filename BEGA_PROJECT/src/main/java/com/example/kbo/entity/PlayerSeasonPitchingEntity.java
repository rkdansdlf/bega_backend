package com.example.kbo.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "player_season_pitching")
@Immutable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerSeasonPitchingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "player_id", nullable = false)
    private Integer playerId;

    @Column(name = "season", nullable = false)
    private Integer season;

    @Column(name = "league", nullable = false, length = 50)
    private String league;

    @Column(name = "level", nullable = false, length = 50)
    private String level;

    @Column(name = "source", nullable = false, length = 50)
    private String source;

    @Column(name = "team_code", length = 20)
    private String teamCode;

    @Column(name = "games")
    private Integer games;

    @Column(name = "games_started")
    private Integer gamesStarted;

    @Column(name = "wins")
    private Integer wins;

    @Column(name = "losses")
    private Integer losses;

    @Column(name = "saves")
    private Integer saves;

    @Column(name = "holds")
    private Integer holds;

    @Column(name = "innings_pitched")
    private Double inningsPitched;

    @Column(name = "hits_allowed")
    private Integer hitsAllowed;

    @Column(name = "runs_allowed")
    private Integer runsAllowed;

    @Column(name = "earned_runs")
    private Integer earnedRuns;

    @Column(name = "home_runs_allowed")
    private Integer homeRunsAllowed;

    @Column(name = "walks_allowed")
    private Integer walksAllowed;

    @Column(name = "intentional_walks")
    private Integer intentionalWalks;

    @Column(name = "hit_batters")
    private Integer hitBatters;

    @Column(name = "strikeouts")
    private Integer strikeouts;

    @Column(name = "wild_pitches")
    private Integer wildPitches;

    @Column(name = "balks")
    private Integer balks;

    @Column(name = "era")
    private Double era;

    @Column(name = "whip")
    private Double whip;

    @Column(name = "fip")
    private Double fip;

    @Column(name = "k_per_nine")
    private Double kPerNine;

    @Column(name = "bb_per_nine")
    private Double bbPerNine;

    @Column(name = "kbb")
    private Double kbb;

    @Column(name = "complete_games")
    private Integer completeGames;

    @Column(name = "shutouts")
    private Integer shutouts;

    @Column(name = "quality_starts")
    private Integer qualityStarts;

    @Column(name = "blown_saves")
    private Integer blownSaves;

    @Column(name = "tbf")
    private Integer tbf;

    @Column(name = "np")
    private Integer np;

    @Column(name = "avg_against")
    private Double avgAgainst;

    @Column(name = "doubles_allowed")
    private Integer doublesAllowed;

    @Column(name = "triples_allowed")
    private Integer triplesAllowed;

    @Column(name = "sacrifices_allowed")
    private Integer sacrificesAllowed;

    @Column(name = "sacrifice_flies_allowed")
    private Integer sacrificeFliesAllowed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_stats")
    private JsonNode extraStats;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "season_id")
    private Integer seasonId;

    @Column(name = "innings_outs")
    private Integer inningsOuts;

    @Column(name = "innings_display")
    private BigDecimal inningsDisplay;
}
