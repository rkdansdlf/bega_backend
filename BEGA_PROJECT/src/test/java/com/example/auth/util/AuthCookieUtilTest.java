package com.example.auth.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

class AuthCookieUtilTest {

    @Test
    @DisplayName("인증 쿠키는 HttpOnly, SameSite=Lax 플래그를 포함한다")
    void buildAuthCookie_setsSecurityFlags() {
        AuthCookieUtil authCookieUtil = new AuthCookieUtil(true);

        ResponseCookie cookie = authCookieUtil.buildAuthCookie("jwt-token", 3600);

        assertThat(cookie.toString()).contains("Authorization=jwt-token");
        assertThat(cookie.toString()).contains("HttpOnly");
        assertThat(cookie.toString()).contains("Secure");
        assertThat(cookie.toString()).contains("SameSite=Lax");
    }

    @Test
    @DisplayName("쿠키 값에 CRLF가 포함되면 헤더 생성 전에 차단한다")
    void buildRefreshCookie_rejectsCrlfInValue() {
        AuthCookieUtil authCookieUtil = new AuthCookieUtil(false);

        assertThatThrownBy(() -> authCookieUtil.buildRefreshCookie("token\r\nSet-Cookie: admin=true", 3600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CR or LF");
    }

    @Test
    @DisplayName("인증 쿠키 값에 CRLF가 포함되면 헤더 생성 전에 차단한다")
    void buildAuthCookie_rejectsCrlfInValue() {
        AuthCookieUtil authCookieUtil = new AuthCookieUtil(false);

        assertThatThrownBy(() -> authCookieUtil.buildAuthCookie("token\r\nSet-Cookie: admin=true", 3600))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CR or LF");
    }
}
