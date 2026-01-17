package com.example.auth.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.ExpiredJwtException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JWTUtil {

    private final SecretKey secretKey;
    private final long refreshExpirationTime;

    public JWTUtil(@Value("${spring.jwt.secret}") String secret,
            @Value("${spring.jwt.refresh-expiration}") long refreshExpirationTime) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.refreshExpirationTime = refreshExpirationTime;
    }

    // Access Token 생성
    public String createJwt(String email, String role, Long userId, long expiredMs) {
        return Jwts.builder()
                .claim("email", email)
                .claim("role", role)
                .claim("user_id", userId)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiredMs))
                .signWith(secretKey)
                .compact();
    }

    // Refresh Token 생성
    public String createRefreshToken(String email, String role, Long userId) {
        return Jwts.builder()
                .claim("email", email)
                .claim("role", role)
                .claim("user_id", userId)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + refreshExpirationTime))
                .signWith(secretKey)
                .compact();
    }

    // JWT에서 특정 Claim 가져오기
    @Cacheable(value = "jwtUserCache", key = "#token")
    private Claims getClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }

    // Email 추출 (캐싱 적용)
    public String getEmail(String token) {
        return getClaims(token).get("email", String.class);
    }

    // User ID 추출 (캐싱 적용)
    public Long getUserId(String token) {
        return getClaims(token).get("user_id", Long.class);
    }

    // Role 추출 (캐싱 적용)
    public String getRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    // JWT 만료 여부 확인
    public Boolean isExpired(String token) {
        try {
            return getClaims(token).getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    // Refresh Token 만료 시간을 외부에 노출
    public long getRefreshTokenExpirationTime() {
        return refreshExpirationTime;
    }
}