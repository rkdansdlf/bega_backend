package com.example.stadium.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceDto {
    private Long id;
    private String stadiumName;
    private String category;
    private String name;
    private String description;
    private double lat;
    private double lng;
    private String address;
    private String phone;
    private Double rating;
    private String openTime;
    private String closeTime;
}