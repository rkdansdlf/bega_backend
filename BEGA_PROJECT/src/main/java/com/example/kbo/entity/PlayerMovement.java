package com.example.kbo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "player_movements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "movement_date", nullable = false)
    private LocalDate movementDate;

    @Column(nullable = false, length = 50)
    private String section; // e.g., FA, 트레이드, 군보류 등

    @Column(name = "team_code", nullable = false, length = 20)
    private String teamCode;

    @Column(name = "player_name", nullable = false, length = 100)
    private String playerName;

    @Column(length = 300)
    private String summary;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.LONG32VARCHAR)
    @Column(name = "details")
    private String details; // Contract details or notes

    @Column(name = "contract_term", length = 100)
    private String contractTerm;

    @Column(name = "contract_value", length = 120)
    private String contractValue;

    @Column(name = "option_details", length = 300)
    private String optionDetails;

    @Column(name = "counterparty_team", length = 50)
    private String counterpartyTeam;

    @Column(name = "counterparty_details", length = 500)
    private String counterpartyDetails;

    @Column(name = "source_label", length = 100)
    private String sourceLabel;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "announced_at")
    private LocalDateTime announcedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
