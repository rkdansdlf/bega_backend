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
@Table(name = "stadiums", schema = "public")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stadium {
    
    @Id
    @Column(name = "stadium_id", columnDefinition = "varchar")
    private String stadiumId;
    
    @Column(name = "stadium_name", columnDefinition = "varchar")
    private String stadiumName;
    
    @Column(name = "city", columnDefinition = "varchar")
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
    
    @Column(name = "turf_type", columnDefinition = "varchar")
    private String turfType;
    
    @Column(name = "bullpen_type", columnDefinition = "varchar")
    private String bullpenType;
    
    @Column(name = "homerun_park_factor")
    private Double homerunParkFactor;
    
    @Column(name = "notes", columnDefinition = "text")
    private String notes;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "lat")
    private Double lat;
    
    @Column(name = "lng")
    private Double lng;
    
    @Column(name = "address", columnDefinition = "varchar")
    private String address;
    
    @Column(name = "phone", columnDefinition = "varchar")
    private String phone;
    
    @Column(name = "team", columnDefinition = "varchar")
    private String team;
    
    @OneToMany(mappedBy = "stadium", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Place> places = new ArrayList<>();
}