package com.example.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicUserProfileDto {
    private Long id;
    private String name;
    private String favoriteTeam;
    private String profileImageUrl;
    private String bio;
}
