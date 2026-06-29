package com.example.mate.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_party_favorites",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "party_id"}))
public class UserPartyFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public UserPartyFavorite() {}

    public UserPartyFavorite(Long userId, Long partyId) {
        this.userId = userId;
        this.partyId = partyId;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getPartyId() { return partyId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
