package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "award")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AwardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "award_type", nullable = false)
    private String awardType; // MVP, Rookie, Golden Glove

    @Column(name = "player_name", nullable = false)
    private String playerName;

    @Column(name = "year", nullable = false)
    private int year;

    @Column(name = "position")
    private String position; // P, C, 1B, etc. (Can be null)

    // Assuming team is stored or reachable via player.
    // For simplicity in this iteration, we add it here or we fetch it.
    // Based on user request "award_type, player_name, year", team seems missing
    // from schema.
    // We will assume it can be derived or is added for convenience.
    @Transient // Not in DB based on description, but needed for DTO. Will be mocked/derived.
    private String team;
}
