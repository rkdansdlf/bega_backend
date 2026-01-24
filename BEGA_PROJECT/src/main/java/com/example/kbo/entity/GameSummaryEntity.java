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

import java.time.OffsetDateTime;

@Entity
@Table(name = "game_summary")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameSummaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "game_id", nullable = false, length = 20)
    private String gameId;

    @Column(name = "summary_type", length = 50)
    private String summaryType;

    @Column(name = "player_id")
    private Integer playerId;

    @Column(name = "player_name", length = 50)
    private String playerName;

    @Column(name = "detail_text", columnDefinition = "text")
    private String detailText;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
