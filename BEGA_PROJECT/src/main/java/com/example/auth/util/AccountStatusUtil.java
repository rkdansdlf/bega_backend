package com.example.auth.util;

import com.example.auth.entity.UserEntity;
import java.time.LocalDateTime;

public final class AccountStatusUtil {

    private AccountStatusUtil() {
    }

    public static boolean isAccountUsable(UserEntity user) {
        if (user == null) {
            return false;
        }
        if (!user.isLocked()) {
            return true;
        }
        if (user.getLockExpiresAt() == null) {
            return false;
        }
        return user.getLockExpiresAt().isBefore(LocalDateTime.now());
    }

    public static int currentTokenVersion(UserEntity user) {
        if (user == null || user.getTokenVersion() == null) {
            return 0;
        }
        return user.getTokenVersion();
    }

    public static boolean hasMatchingTokenVersion(UserEntity user, Integer tokenVersionInToken) {
        int currentTokenVersion = currentTokenVersion(user);
        if (tokenVersionInToken == null) {
            return currentTokenVersion == 0;
        }
        return currentTokenVersion == tokenVersionInToken;
    }
}
