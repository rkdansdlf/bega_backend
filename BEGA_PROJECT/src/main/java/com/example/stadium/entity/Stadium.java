package com.example.stadium.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stadiums")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stadium {

    @Id
    @Column(name = "stadium_id")
    private String stadiumId;

    @Column(name = "stadium_name")
    private String stadiumName;

    @Column(name = "city")
    private String city;

    @Column(name = "open_year")
    private Integer openYear;

    @Column(name = "capacity")
    private Integer capacity;

    @Column(name = "seating_capacity")
    private Integer seatingCapacity;

    @Column(name = "left_fence_m")
    private Double leftFenceM;

    @Column(name = "center_fence_m")
    private Double centerFenceM;

    @Column(name = "fence_height_m")
    private Double fenceHeightM;

    @Column(name = "turf_type")
    private String turfType;

    @Column(name = "bullpen_type")
    private String bullpenType;

    @Column(name = "homerun_park_factor")
    private Double homerunParkFactor;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "lat")
    private Double lat;

    @Column(name = "lng")
    private Double lng;

    @Column(name = "address")
    private String address;

    @Column(name = "phone")
    private String phone;

    @Column(name = "team")
    private String team;

    @Builder.Default
    @OneToMany(mappedBy = "stadium", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Place> places = new ArrayList<>();
}