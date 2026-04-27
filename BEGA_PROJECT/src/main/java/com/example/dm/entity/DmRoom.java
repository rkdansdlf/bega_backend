package com.example.dm.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "dm_rooms",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_dm_rooms_participants",
                columnNames = { "participant_one_id", "participant_two_id" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DmRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "participant_one_id", nullable = false)
    private Long participantOneId;

    @Column(name = "participant_two_id", nullable = false)
    private Long participantTwoId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
