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
    private final long refreshExpirationTime; 

    public JWTUtil(@Value("${spring.jwt.secret}") String secret, 
                   @Value("${spring.jwt.refresh-expiration}") long refreshExpirationTime) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.refreshExpirationTime = refreshExpirationTime;
    }

    // Access Token 생성 
    public String createJwt(String email, String role, long expiredMs) { 

        return Jwts.builder()
                .claim("email", email) // Claim 키를 email로 설정
                .claim("role", role)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiredMs))
                .signWith(secretKey)
                .compact();
    }
    
    // Refresh Token 생성
    public String createRefreshToken(String email, String role) { 
        
        return Jwts.builder()
                .claim("email", email) // Claim 키를 email로 변경
                .claim("role", role)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + refreshExpirationTime))
                .signWith(secretKey)
                .compact();
    }

    // JWT에서 특정 Claim 가져오기
    private Claims getClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            // 토큰이 만료되었을 때도 Claims를 반환하여 email, role 등을 가져올 수 있도록 하기
            return e.getClaims(); 
        }
    }

    // JWT에서 Email 추출
    public String getEmail(String token) { 
        // 토큰이 만료되었더라도 Claims를 얻어 email을 가져올 수 있음
        return getClaims(token).get("email", String.class);
    }

    // JWT에서 Role 추출
    public String getRole(String token) {
        // Role Claim을 String 형태로 추출합니다.
        return getClaims(token).get("role", String.class);
    }

    // JWT 만료 여부 확인
    public Boolean isExpired(String token) {
        try {
            // 만료 날짜를 기준으로 현재 시간이 이후인지 확인
            return getClaims(token).getExpiration().before(new Date());
        } catch (Exception e) {
            // 토큰 파싱 실패 시, 만료된 것으로 간주하거나 잘못된 토큰으로 처리
            return true;
        }
    }
    
    // Refresh Token 만료 시간을 외부에 노출
    public long getRefreshTokenExpirationTime() {
        return refreshExpirationTime;
    }
}
