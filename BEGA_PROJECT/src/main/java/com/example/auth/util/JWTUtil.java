package com.example.auth.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.MacAlgorithm;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.Map;

@Slf4j
@Component
public class JWTUtil {

    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String TOKEN_VERSION_CLAIM = "token_version";
    private static final String SESSION_ID_CLAIM = "session_id";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_LINK = "link";
    private static final MacAlgorithm SIGNATURE_ALGORITHM = Jwts.SIG.HS512;
    private static final String SIGNATURE_ALGORITHM_ID = "HS512";
    private static final int MIN_HS512_SECRET_BYTES = 64;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> JWT_HEADER_TYPE = new TypeReference<>() {
    };

    private final String secret;
    private final SecretKey secretKey;
    private final long refreshExpirationTime;
    @Value("${spring.jwt.access-expiration:7200000}")
    private long accessExpirationTime;

    public JWTUtil(@Value("${spring.jwt.secret}") String secret,
            @Value("${spring.jwt.refresh-expiration}") long refreshExpirationTime) {
        this.secret = secret;
        this.secretKey = Keys.hmacShaKeyFor(validateAndGetSecretBytes(secret));
        this.refreshExpirationTime = refreshExpirationTime;
    }

    @PostConstruct
    public void validateSecret() {
        validateAndGetSecretBytes(secret);
        if (accessExpirationTime <= 0) {
            throw new IllegalStateException("JWT access expiration must be a positive value.");
        }
    }

    private byte[] validateAndGetSecretBytes(String rawSecret) {
        if (rawSecret == null || rawSecret.isBlank()) {
            throw new IllegalStateException(
                    "JWT secret is not configured properly. It must be at least 64 bytes long for HS512.");
        }

        byte[] secretBytes = rawSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_HS512_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT secret is not configured properly. It must be at least 64 bytes long for HS512.");
        }
        return secretBytes;
    }

    // Access Token 생성
    public String createJwt(String email, String role, Long userId, long expiredMs) {
        return createJwt(email, role, userId, expiredMs, 0);
    }

    // Access Token 생성 (토큰 버전 포함)
    public String createJwt(String email, String role, Long userId, long expiredMs, Integer tokenVersion) {
        return Jwts.builder()
                .claim(TOKEN_TYPE_CLAIM, TYPE_ACCESS)
                .claim("role", role)
                .claim("user_id", userId)
                .claim(TOKEN_VERSION_CLAIM, tokenVersion == null ? 0 : tokenVersion)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiredMs))
                .signWith(secretKey, SIGNATURE_ALGORITHM)
                .compact();
    }

    // Link Token 생성 (계정 연동용)
    public String createLinkToken(Long userId, long expiredMs) {
        return Jwts.builder()
                .claim(TOKEN_TYPE_CLAIM, TYPE_LINK)
                .claim("user_id", userId)
                .claim("role", "LINK_MODE") // 호환성을 위해 유지하되, Filter에서 type 체크로 차단됨
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiredMs))
                .signWith(secretKey, SIGNATURE_ALGORITHM)
                .compact();
    }

    // Refresh Token 생성
    public String createRefreshToken(String email, String role, Long userId) {
        return createRefreshToken(email, role, userId, 0);
    }

    public String createRefreshToken(String email, String role, Long userId, Integer tokenVersion) {
        return createRefreshToken(email, role, userId, tokenVersion, null);
    }

    public String createRefreshToken(String email, String role, Long userId, Integer tokenVersion, String sessionId) {
        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
                .claim(TOKEN_TYPE_CLAIM, "refresh")
                .claim("role", role)
                .claim("user_id", userId)
                .claim(TOKEN_VERSION_CLAIM, tokenVersion == null ? 0 : tokenVersion)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + refreshExpirationTime));

        if (sessionId != null && !sessionId.isBlank()) {
            builder.claim(SESSION_ID_CLAIM, sessionId.trim());
        }

        return builder
                .signWith(secretKey, SIGNATURE_ALGORITHM)
                .compact();
    }

    // [Security Fix - Critical #1] 토큰 원문을 캐시 key로 쓰지 않고 SHA-256 해시를 사용.
    // 원문 토큰이 cache store(Redis/Caffeine)에 평문 저장되면 스냅샷 유출 시
    // 모든 유효 토큰이 그대로 노출되므로, 단방향 해시를 key로 사용한다.
    public static String hashKey(String token) {
        if (token == null) {
            return "null";
        }
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    // JWT에서 특정 Claim 가져오기 (해시된 token을 cache key로 사용)
    @Cacheable(value = "jwtUserCache", key = "T(com.example.auth.util.JWTUtil).hashKey(#token)")
    public Claims getClaims(String token) {
        try {
            validateSignatureAlgorithm(token);
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .sig().add(SIGNATURE_ALGORITHM).and()
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }

    private void validateSignatureAlgorithm(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.", -1);
        if (parts.length != 3 || parts[0].isBlank()) {
            throw new MalformedJwtException("Malformed JWT");
        }

        try {
            byte[] headerBytes = Base64.getUrlDecoder().decode(parts[0]);
            Map<String, Object> header = OBJECT_MAPPER.readValue(headerBytes, JWT_HEADER_TYPE);
            Object algorithm = header.get("alg");
            if (!SIGNATURE_ALGORITHM_ID.equals(algorithm)) {
                throw new UnsupportedJwtException("Unsupported JWT signature algorithm");
            }
        } catch (IllegalArgumentException | IOException e) {
            throw new MalformedJwtException("Malformed JWT header", e);
        }
    }

    // Email 추출 (캐싱 적용)
    public String getEmail(String token) {
        return getClaims(token).get("email", String.class);
    }

    // User ID 추출 (캐싱 적용)
    public Long getUserId(String token) {
        Object rawUserId = getClaims(token).get("user_id");
        if (rawUserId == null) {
            return null;
        }

        if (rawUserId instanceof Long) {
            return (Long) rawUserId;
        }

        if (rawUserId instanceof Integer) {
            return ((Integer) rawUserId).longValue();
        }

        if (rawUserId instanceof Number) {
            return ((Number) rawUserId).longValue();
        }

        if (rawUserId instanceof String) {
            try {
                return Long.parseLong((String) rawUserId);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }

    // Token Version 추출
    public Integer getTokenVersion(String token) {
        Object rawTokenVersion = getClaims(token).get(TOKEN_VERSION_CLAIM);
        if (rawTokenVersion == null) {
            return null;
        }

        if (rawTokenVersion instanceof Integer) {
            return (Integer) rawTokenVersion;
        }

        if (rawTokenVersion instanceof Long) {
            return ((Long) rawTokenVersion).intValue();
        }

        if (rawTokenVersion instanceof Number) {
            return ((Number) rawTokenVersion).intValue();
        }

        if (rawTokenVersion instanceof String) {
            try {
                return Integer.parseInt((String) rawTokenVersion);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }

    public String getSessionId(String token) {
        String sessionId = getClaims(token).get(SESSION_ID_CLAIM, String.class);
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }

        return sessionId.trim();
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

    public long getAccessTokenExpirationTime() {
        return accessExpirationTime;
    }

    /**
     * 토큰 캐시 무효화 (로그아웃 시 호출)
     * [Security Fix] 로그아웃된 토큰의 캐시된 Claims 정보 제거
     * @param token 무효화할 토큰
     */
    @CacheEvict(value = "jwtUserCache", key = "T(com.example.auth.util.JWTUtil).hashKey(#token)")
    public void evictTokenCache(String token) {
        // [Security Fix - High #2] 토큰 prefix 로깅 제거 (CWE-532).
        // 필요 시 log.debug("Evicted token cache for key {}", hashKey(token)) 형태로 대체 가능.
        log.debug("Evicted token cache entry");
    }
}
