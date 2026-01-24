package com.example.kbo.entity;

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
@Table(name = "game_inning_scores")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameInningScoreEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "game_id", nullable = false, length = 20)
    private String gameId;

    @Column(name = "team_side", nullable = false, length = 10)
    private String teamSide;

    @Column(name = "team_code", length = 10)
    private String teamCode;

    @Column(name = "inning", nullable = false)
    private Integer inning;

    @Column(name = "runs")
    private Integer runs;

    @Column(name = "is_extra")
    private Boolean isExtra;
}
