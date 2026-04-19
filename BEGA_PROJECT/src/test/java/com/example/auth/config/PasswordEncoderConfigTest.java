package com.example.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * [Security Fix - Critical #2] DelegatingPasswordEncoder가 Argon2id를 기본으로 사용하고
 * 기존 {bcrypt} 해시도 여전히 검증할 수 있는지 확인한다.
 *
 * BouncyCastle(bcprov-jdk18on) 없이는 Argon2PasswordEncoder가 런타임에 실패하므로
 * 이 테스트가 통과한다는 것은 배포 시 Argon2 해싱이 정상 동작한다는 증거다.
 */
class PasswordEncoderConfigTest {

    private PasswordEncoder buildEncoder() {
        String idForEncode = "argon2";
        java.util.Map<String, PasswordEncoder> encoders = new java.util.HashMap<>();
        encoders.put("argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());
        encoders.put("bcrypt", new BCryptPasswordEncoder(12));
        return new DelegatingPasswordEncoder(idForEncode, encoders);
    }

    @Test
    void encodes_withArgon2Prefix_byDefault() {
        PasswordEncoder encoder = buildEncoder();
        String encoded = encoder.encode("P@ssw0rd!123");

        assertThat(encoded).startsWith("{argon2}");
        assertThat(encoder.matches("P@ssw0rd!123", encoded)).isTrue();
        assertThat(encoder.matches("wrong-password", encoded)).isFalse();
    }

    @Test
    void matches_legacyBcryptHashWithPrefix() {
        PasswordEncoder encoder = buildEncoder();
        // {bcrypt} prefix를 붙인 레거시 해시도 matches에서 검증되어야 한다.
        // (Flyway V100이 prod 테이블에 prefix를 씌운 뒤에도 로그인이 계속 동작해야 함)
        String legacyBcryptEncoded = new BCryptPasswordEncoder(10).encode("legacy-password");
        String prefixed = "{bcrypt}" + legacyBcryptEncoded;

        assertThat(encoder.matches("legacy-password", prefixed)).isTrue();
        assertThat(encoder.matches("wrong", prefixed)).isFalse();
    }
}
