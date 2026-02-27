package com.example.stadium.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_stadium_favorites",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "stadium_id"}))
public class UserStadiumFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "stadium_id", nullable = false, length = 20)
    private String stadiumId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public UserStadiumFavorite() {}

    public UserStadiumFavorite(Long userId, String stadiumId) {
        this.userId = userId;
        this.stadiumId = stadiumId;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getStadiumId() { return stadiumId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
