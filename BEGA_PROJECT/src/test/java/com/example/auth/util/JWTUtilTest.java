package com.example.auth.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * [Security Fix - Critical #1] JWTUtil.hashKey()가 캐시 키로 쓰이는 SHA-256 해시를
 * 올바르게 계산하는지 검증한다. 토큰 원문이 cache store에 노출되면 안 되므로,
 * 해시 함수의 결정성/충돌 저항/출력 형식이 회귀되지 않도록 가드한다.
 */
class JWTUtilTest {

    private static final String VALID_SECRET =
            "test-jwt-secret-64-characters-long-for-hs512-signature-tests-key-1234567890";
    private static final long REFRESH_EXPIRATION_MS = 604800000L;
    private static final String SHA256_EMPTY_STRING_HEX =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Test
    void hashKey_returnsLiteralNull_whenTokenIsNull() {
        assertThat(JWTUtil.hashKey(null)).isEqualTo("null");
    }

    @Test
    void hashKey_returnsSha256OfEmptyString_whenTokenIsEmpty() {
        assertThat(JWTUtil.hashKey("")).isEqualTo(SHA256_EMPTY_STRING_HEX);
    }

    @Test
    void hashKey_isDeterministic_sameInputProducesSameHash() {
        String token = "eyJhbGciOiJIUzI1NiJ9.sample.payload";
        String first = JWTUtil.hashKey(token);
        String second = JWTUtil.hashKey(token);
        assertThat(first).isEqualTo(second);
    }

    @Test
    void hashKey_producesDifferentHashes_forDifferentInputs() {
        String h1 = JWTUtil.hashKey("token-one");
        String h2 = JWTUtil.hashKey("token-two");
        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    void hashKey_outputIs64HexCharacters() {
        String hash = JWTUtil.hashKey("some-arbitrary-jwt-value.xyz");
        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
    }

    @Test
    void hashKey_doesNotLeakOriginalToken() {
        String token = "secret-token-abc123";
        String hash = JWTUtil.hashKey(token);
        assertThat(hash).doesNotContain(token);
    }

    @Test
    void validateSecret_rejectsSecretsShorterThanSixtyFourBytes() {
        assertThatThrownBy(() -> new JWTUtil("12345678901234567890123456789012", REFRESH_EXPIRATION_MS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("64 bytes");
    }

    @Test
    void createJwt_signsWithHs512AndOmitsEmailClaim() {
        JWTUtil jwtUtil = buildJwtUtil();

        String token = jwtUtil.createJwt("user@example.com", "ROLE_USER", 123L, 60000L, 7);

        String headerJson = new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]), StandardCharsets.UTF_8);
        assertThat(headerJson).contains("\"alg\":\"HS512\"");
        assertThat(jwtUtil.getEmail(token)).isNull();
        assertThat(jwtUtil.getUserId(token)).isEqualTo(123L);
        assertThat(jwtUtil.getRole(token)).isEqualTo("ROLE_USER");
        assertThat(jwtUtil.getTokenType(token)).isEqualTo("access");
        assertThat(jwtUtil.getTokenVersion(token)).isEqualTo(7);
    }

    @Test
    void getEmail_readsLegacyEmailClaimWhenPresent() {
        JWTUtil jwtUtil = buildJwtUtil();
        String legacyToken = Jwts.builder()
                .claim("email", "legacy@example.com")
                .claim("user_id", 321L)
                .signWith(Keys.hmacShaKeyFor(VALID_SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS512)
                .compact();

        assertThat(jwtUtil.getEmail(legacyToken)).isEqualTo("legacy@example.com");
        assertThat(jwtUtil.getUserId(legacyToken)).isEqualTo(321L);
    }

    @Test
    void getClaims_rejectsTokenSignedWithDifferentHmacAlgorithm() {
        JWTUtil jwtUtil = buildJwtUtil();
        String hs256Token = Jwts.builder()
                .claim("role", "ROLE_USER")
                .claim("user_id", 123L)
                .signWith(Keys.hmacShaKeyFor(VALID_SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwtUtil.getClaims(hs256Token))
                .isInstanceOf(JwtException.class);
    }

    private JWTUtil buildJwtUtil() {
        JWTUtil jwtUtil = new JWTUtil(VALID_SECRET, REFRESH_EXPIRATION_MS);
        ReflectionTestUtils.setField(jwtUtil, "accessExpirationTime", 7200000L);
        jwtUtil.validateSecret();
        return jwtUtil;
    }
}
