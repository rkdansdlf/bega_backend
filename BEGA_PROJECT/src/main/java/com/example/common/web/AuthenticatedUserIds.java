package com.example.common.web;

import com.example.common.exception.AuthenticationRequiredException;

public final class AuthenticatedUserIds {

    private AuthenticatedUserIds() {
    }

    public static Long require(Long userId) {
        return require(userId, "인증이 필요합니다.");
    }

    public static Long require(Long userId, String message) {
        if (userId == null) {
            throw new AuthenticationRequiredException(message);
        }
        return userId;
    }
}
