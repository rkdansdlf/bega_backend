package com.example.auth.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.ExpiredJwtException;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JWTUtil {

    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_LINK = "link";

    private final String secret;
    private final SecretKey secretKey;
    private final long refreshExpirationTime;

    public JWTUtil(@Value("${spring.jwt.secret}") String secret,
            @Value("${spring.jwt.refresh-expiration}") long refreshExpirationTime) {
        this.secret = secret;
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.refreshExpirationTime = refreshExpirationTime;
    }

    @PostConstruct
    public void validateSecret() {
        if (secret == null || secret.isBlank() || secret.length() < 32) {
            throw new IllegalStateException(
                    "JWT secret is not configured properly. It must be at least 32 characters long.");
        }
    }

    // Access Token 생성
    public String createJwt(String email, String role, Long userId, long expiredMs) {
        return Jwts.builder()
                .claim(TOKEN_TYPE_CLAIM, TYPE_ACCESS)
                .claim("email", email)
                .claim("role", role)
                .claim("user_id", userId)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiredMs))
                .signWith(secretKey)
                .compact();
    }

    // Link Token 생성 (계정 연동용)
    public String createLinkToken(Long userId, long expiredMs) {
        return Jwts.builder()
                .claim(TOKEN_TYPE_CLAIM, TYPE_LINK)
                .claim("user_id", userId)
                .claim("role", "LINK_MODE") // 호환성을 위해 유지하되, Filter에서 type 체크로 차단됨
                .claim("email", "link-action") // 호환성을 위해 유지
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiredMs))
                .signWith(secretKey)
                .compact();
    }

    // Refresh Token 생성
    public String createRefreshToken(String email, String role, Long userId) {
        return Jwts.builder()
                .claim(TOKEN_TYPE_CLAIM, "refresh")
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

    // Token Type 추출
    public String getTokenType(String token) {
        return getClaims(token).get(TOKEN_TYPE_CLAIM, String.class);
    }

    // JWT 만료 여부 확인
    public Boolean isExpired(String token) {
        try {
            return getClaims(token).getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    // JWT 만료 시간 추출
    public Date getExpiration(String token) {
        return getClaims(token).getExpiration();
    }

    // Refresh Token 만료 시간을 외부에 노출
    public long getRefreshTokenExpirationTime() {
        return refreshExpirationTime;
    }
}