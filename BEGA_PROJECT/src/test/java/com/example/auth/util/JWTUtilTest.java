package com.example.auth.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * [Security Fix - Critical #1] JWTUtil.hashKey()가 캐시 키로 쓰이는 SHA-256 해시를
 * 올바르게 계산하는지 검증한다. 토큰 원문이 cache store에 노출되면 안 되므로,
 * 해시 함수의 결정성/충돌 저항/출력 형식이 회귀되지 않도록 가드한다.
 */
class JWTUtilTest {

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
}
