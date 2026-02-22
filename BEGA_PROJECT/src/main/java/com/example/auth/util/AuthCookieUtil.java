package com.example.auth.util;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 인증용 공통 쿠키 생성을 위한 유틸리티.
 */
@Component
public class AuthCookieUtil {

    private final boolean secureCookie;

    public AuthCookieUtil(@Value("${app.cookie.secure:false}") boolean secureCookie) {
        this.secureCookie = secureCookie;
    }

    public ResponseCookie buildAuthCookie(String token, long maxAgeSeconds) {
        return build("Authorization", token, maxAgeSeconds);
    }

    public ResponseCookie buildRefreshCookie(String token, long maxAgeSeconds) {
        return build("Refresh", token, maxAgeSeconds);
    }

    public ResponseCookie buildExpiredAuthCookie() {
        return build("Authorization", "", 0);
    }

    public ResponseCookie buildExpiredRefreshCookie() {
        return build("Refresh", "", 0);
    }

    public void addCookieHeader(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private ResponseCookie build(String name, String value, long maxAgeSeconds) {
        return ResponseCookie.from(name, value != null ? value : "")
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite("Lax")
                .build();
    }
}
