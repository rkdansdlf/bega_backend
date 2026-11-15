package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RefreshTokenDigestServiceTest {

    @Test
    void digestIsDeterministicHmacSha256WithoutRawTokenMaterial() {
        RefreshTokenDigestService service = new RefreshTokenDigestService("test-refresh-token-pepper-value");

        String digest = service.digest("raw-refresh-token");

        assertThat(digest).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(digest).isEqualTo(service.digest("raw-refresh-token"));
        assertThat(digest).doesNotContain("raw-refresh-token");
        assertThat(service.digest("other-refresh-token")).isNotEqualTo(digest);
    }

    @Test
    void digestRejectsBlankToken() {
        RefreshTokenDigestService service = new RefreshTokenDigestService("test-refresh-token-pepper-value");

        assertThatThrownBy(() -> service.digest(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
