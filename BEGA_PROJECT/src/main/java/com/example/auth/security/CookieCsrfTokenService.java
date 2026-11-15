package com.example.auth.security;

import com.example.common.exception.ForbiddenBusinessException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CookieCsrfTokenService {

    public static final String COOKIE_NAME = "XSRF-TOKEN";
    public static final String HEADER_NAME = "X-XSRF-TOKEN";

    private final SecureRandom secureRandom = new SecureRandom();

    public String issueToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public void validate(HttpServletRequest request) {
        String cookieToken = readCookie(request);
        String headerToken = request != null ? request.getHeader(HEADER_NAME) : null;
        if (!StringUtils.hasText(cookieToken)
                || !StringUtils.hasText(headerToken)
                || !MessageDigest.isEqual(
                        cookieToken.getBytes(StandardCharsets.UTF_8),
                        headerToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ForbiddenBusinessException(
                    "INVALID_LOGOUT_CSRF",
                    "로그아웃 CSRF 토큰이 없거나 일치하지 않습니다.");
        }
    }

    private String readCookie(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
