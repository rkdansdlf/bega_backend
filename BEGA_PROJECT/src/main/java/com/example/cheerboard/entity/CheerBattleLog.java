package com.example.cheerboard.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "cheer_battle_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class CheerBattleLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private String gameId;

    @Column(name = "team_id", nullable = false)
    private String teamId;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @CreatedDate
    @Column(name = "voted_at", nullable = false, updatable = false)
    private Instant votedAt;
}
