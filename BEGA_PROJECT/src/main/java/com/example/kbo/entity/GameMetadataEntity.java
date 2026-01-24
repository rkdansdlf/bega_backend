package com.example.kbo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Entity
@Table(name = "game_metadata")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameMetadataEntity {

    @Id
    @Column(name = "game_id", nullable = false, length = 20)
    private String gameId;

    @Column(name = "stadium_code", length = 20)
    private String stadiumCode;

    @Column(name = "stadium_name", length = 50)
    private String stadiumName;

    @Column(name = "attendance")
    private Integer attendance;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "game_time_minutes")
    private Integer gameTimeMinutes;

    @Column(name = "weather", length = 50)
    private String weather;

    @Column(name = "source_payload", columnDefinition = "jsonb")
    private String sourcePayload;
}
