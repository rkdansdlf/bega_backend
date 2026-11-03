package com.example.auth.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

public final class AccessTokenResolver {

    private AccessTokenResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("Authorization".equals(cookie.getName()) && cookie.getValue() != null) {
                    return cookie.getValue();
                }
                if ("Authorization".equals(cookie.getName())) {
                    break;
                }
            }
        }
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }
}
