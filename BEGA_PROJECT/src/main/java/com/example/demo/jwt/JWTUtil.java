package com.example.demo.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.ExpiredJwtException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JWTUtil {

    private final SecretKey secretKey;
    private final long refreshExpirationTime; // Refresh Token 만료 시간 (ms)

    // application.yml에서 secretKey와 refreshExpirationTime을 주입받습니다.
    public JWTUtil(@Value("${spring.jwt.secret}") String secret, 
                   @Value("${spring.jwt.refresh-expiration}") long refreshExpirationTime) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.refreshExpirationTime = refreshExpirationTime;
    }

    // Access Token 생성 메서드
    public String createJwt(String username, String role, long expiredMs) {

        // 🚨 JWT Claims에 username과 role (단일 String)을 추가합니다.
        return Jwts.builder()
                .claim("username", username)
                .claim("role", role) // 👈 단일 Role String을 Claim에 추가
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiredMs))
                .signWith(secretKey)
                .compact();
    }
    
    // Refresh Token 생성 메서드 (만료 시간은 설정값 사용)
    public String createRefreshToken(String username, String role) {
        
        // Refresh Token에도 동일하게 role을 추가 (토큰 재발급 시 사용)
        return Jwts.builder()
                .claim("username", username)
                .claim("role", role)
                // Refresh Token은 Access Token보다 긴 만료 시간(refreshExpirationTime)을 가집니다.
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + refreshExpirationTime))
                .signWith(secretKey)
                .compact();
    }

    // JWT에서 특정 Claim 추출
    private Claims getClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            // 토큰이 만료되었을 때도 Claims를 반환하여 username, role 등을 추출할 수 있도록 합니다.
            return e.getClaims(); 
        }
    }

    // JWT에서 Username 추출
    public String getUsername(String token) {
        // 토큰이 만료되었더라도 Claims를 얻어 username을 추출할 수 있습니다.
        return getClaims(token).get("username", String.class);
    }

    // JWT에서 Role (단일 String) 추출
    public String getRole(String token) {
        // 🚨 Role Claim을 String 형태로 추출합니다.
        return getClaims(token).get("role", String.class);
    }

    // JWT 만료 여부 확인
    public Boolean isExpired(String token) {
        try {
            // 만료 날짜를 기준으로 현재 시간이 이후인지 확인
            return getClaims(token).getExpiration().before(new Date());
        } catch (Exception e) {
            // 토큰 파싱 실패 시, 만료된 것으로 간주하거나 잘못된 토큰으로 처리할 수 있습니다.
            // 여기서는 단순 만료 체크만 수행
            return true;
        }
    }
    
    // Refresh Token 만료 시간을 외부에 노출
    public long getRefreshTokenExpirationTime() {
        return refreshExpirationTime;
    }
}
