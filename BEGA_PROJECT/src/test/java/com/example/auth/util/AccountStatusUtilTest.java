package com.example.auth.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.entity.UserEntity;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AccountStatusUtilTest {

    @Test
    void isAccountUsableAllowsUnlockedAccounts() {
        UserEntity user = UserEntity.builder()
                .locked(false)
                .build();

        assertThat(AccountStatusUtil.isAccountUsable(user)).isTrue();
    }

    @Test
    void isAccountUsableRejectsLockedAccountsWithoutExpiry() {
        UserEntity user = UserEntity.builder()
                .locked(true)
                .lockExpiresAt(null)
                .build();

        assertThat(AccountStatusUtil.isAccountUsable(user)).isFalse();
    }

    @Test
    void isAccountUsableAllowsExpiredLocks() {
        UserEntity user = UserEntity.builder()
                .locked(true)
                .lockExpiresAt(LocalDateTime.now().minusMinutes(1))
                .build();

        assertThat(AccountStatusUtil.isAccountUsable(user)).isTrue();
    }

    @Test
    void hasMatchingTokenVersionAllowsLegacyTokenOnlyWhenCurrentVersionIsZero() {
        UserEntity user = UserEntity.builder()
                .tokenVersion(null)
                .build();

        assertThat(AccountStatusUtil.hasMatchingTokenVersion(user, null)).isTrue();
        assertThat(AccountStatusUtil.hasMatchingTokenVersion(user, 0)).isTrue();
        assertThat(AccountStatusUtil.hasMatchingTokenVersion(user, 1)).isFalse();
    }
}
