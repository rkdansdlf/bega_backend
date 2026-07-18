package com.example.auth.service;

public record ReissuedTokens(
        String accessToken,
        long accessTokenMaxAgeSeconds,
        String refreshToken,
        int refreshTokenMaxAgeSeconds) {
}
