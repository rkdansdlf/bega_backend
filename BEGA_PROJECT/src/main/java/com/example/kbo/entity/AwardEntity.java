package com.example.kbo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "awards")
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

    @Column(name = "award_year", nullable = false)
    private int year;

    // 크롤러(KBO_playwright) 스키마에 없고 이 저장소 어디서도 읽지 않아 제거했다 [awards.position]
    // (2026-08-17). 크롤러가 ADB 스키마를 만들면 존재하지 않을 컬럼이다.

    // Assuming team is stored or reachable via player.
    // For simplicity in this iteration, we add it here or we fetch it.
    // Based on user request "award_type, player_name, year", team seems missing
    // from schema.
    // We will assume it can be derived or is added for convenience.
    @Transient // Not in DB based on description, but needed for DTO. Will be mocked/derived.
    private String team;
}
