package com.example.auth.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * [Security Fix - High #2] LogMaskingUtil 회귀 방지 테스트.
 */
class LogMaskingUtilTest {

    @Test
    void maskEmail_masksLocalPartKeepingFirstCharAndDomain() {
        assertThat(LogMaskingUtil.maskEmail("alice@example.com")).isEqualTo("a****@example.com");
    }

    @Test
    void maskEmail_handlesTwoCharLocalPart() {
        assertThat(LogMaskingUtil.maskEmail("ab@example.com")).isEqualTo("a*@example.com");
    }

    @Test
    void maskEmail_handlesSingleCharLocalPart() {
        assertThat(LogMaskingUtil.maskEmail("a@example.com")).isEqualTo("*@example.com");
    }

    @Test
    void maskEmail_returnsNoneForNullOrBlank() {
        assertThat(LogMaskingUtil.maskEmail(null)).isEqualTo("(none)");
        assertThat(LogMaskingUtil.maskEmail("")).isEqualTo("(none)");
        assertThat(LogMaskingUtil.maskEmail("   ")).isEqualTo("(none)");
    }

    @Test
    void maskEmail_stillMasksWhenAtSignMissing() {
        assertThat(LogMaskingUtil.maskEmail("notanemail")).isEqualTo("n*********");
    }

    @Test
    void maskToken_keepsFirst4CharsAndLength() {
        String token = "eyJhbGciOiJIUzI1NiJ9.payload.signature";
        assertThat(LogMaskingUtil.maskToken(token)).isEqualTo("eyJh***(len=" + token.length() + ")");
    }

    @Test
    void maskToken_handlesShortTokenWithoutPadding() {
        assertThat(LogMaskingUtil.maskToken("abc")).isEqualTo("abc***(len=3)");
    }

    @Test
    void maskToken_returnsNoneForNullOrEmpty() {
        assertThat(LogMaskingUtil.maskToken(null)).isEqualTo("(none)");
        assertThat(LogMaskingUtil.maskToken("")).isEqualTo("(none)");
    }
}
