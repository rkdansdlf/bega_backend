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

    @Column(nullable = false)
    private String gameId;

    @Column(nullable = false)
    private String teamId;

    @Column(nullable = false)
    private String userEmail;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant votedAt;
}
