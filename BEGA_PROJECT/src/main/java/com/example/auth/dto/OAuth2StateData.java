package com.example.auth.dto;

public record OAuth2StateData(
    String email,
    String name,
    String role,
    String profileImageUrl,
    String favoriteTeam,
    String handle
) {}
