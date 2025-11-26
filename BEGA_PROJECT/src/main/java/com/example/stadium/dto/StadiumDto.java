package com.example.stadium.dto;

import lombok.*;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StadiumDto {
    private String stadiumId;      // Long → String
    private String stadiumName;    // name → stadiumName
    private String team;
    private Double lat;
    private Double lng;
    private String address;
    private String phone;
}