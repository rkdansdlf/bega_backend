package com.example.cheerboard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cheer_battle_votes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@jakarta.persistence.IdClass(CheerVoteId.class)
public class CheerVoteEntity {

    @Id
    @Column(name = "game_id")
    private String gameId;

    @Id
    @Column(name = "team_id")
    private String teamId;

    @Column(name = "vote_count")
    private int voteCount;
}
