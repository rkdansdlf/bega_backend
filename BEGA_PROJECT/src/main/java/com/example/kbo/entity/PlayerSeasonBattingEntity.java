package com.example.kbo.entity;

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
@Table(name = "player_season_batting")
@Immutable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerSeasonBattingEntity {

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

    @Column(name = "plate_appearances")
    private Integer plateAppearances;

    @Column(name = "at_bats")
    private Integer atBats;

    @Column(name = "runs")
    private Integer runs;

    @Column(name = "hits")
    private Integer hits;

    @Column(name = "doubles")
    private Integer doubles;

    @Column(name = "triples")
    private Integer triples;

    @Column(name = "home_runs")
    private Integer homeRuns;

    @Column(name = "rbi")
    private Integer rbi;

    @Column(name = "walks")
    private Integer walks;

    @Column(name = "intentional_walks")
    private Integer intentionalWalks;

    @Column(name = "hbp")
    private Integer hbp;

    @Column(name = "strikeouts")
    private Integer strikeouts;

    @Column(name = "stolen_bases")
    private Integer stolenBases;

    @Column(name = "caught_stealing")
    private Integer caughtStealing;

    @Column(name = "sacrifice_hits")
    private Integer sacrificeHits;

    @Column(name = "sacrifice_flies")
    private Integer sacrificeFlies;

    @Column(name = "gdp")
    private Integer gdp;

    @Column(name = "avg")
    private Double avg;

    @Column(name = "obp")
    private Double obp;

    @Column(name = "slg")
    private Double slg;

    @Column(name = "ops")
    private Double ops;

    @Column(name = "iso")
    private Double iso;

    @Column(name = "babip")
    private Double babip;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_stats")
    private JsonNode extraStats;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "season_id")
    private Integer seasonId;
}
