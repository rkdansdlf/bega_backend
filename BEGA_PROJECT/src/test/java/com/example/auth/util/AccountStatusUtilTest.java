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

    @Test
    void isAccountEligibleAllowsNormalAccounts() {
        UserEntity user = UserEntity.builder()
                .enabled(true)
                .pendingDeletion(false)
                .locked(false)
                .build();

        assertThat(AccountStatusUtil.isAccountEligible(user)).isTrue();
    }

    @Test
    void isAccountEligibleRejectsNullOrDisabledOrPendingDeletion() {
        assertThat(AccountStatusUtil.isAccountEligible(null)).isFalse();

        UserEntity disabled = UserEntity.builder()
                .enabled(false)
                .pendingDeletion(false)
                .locked(false)
                .build();
        assertThat(AccountStatusUtil.isAccountEligible(disabled)).isFalse();

        UserEntity pendingDeletion = UserEntity.builder()
                .enabled(true)
                .pendingDeletion(true)
                .locked(false)
                .build();
        assertThat(AccountStatusUtil.isAccountEligible(pendingDeletion)).isFalse();
    }

    @Test
    void isAccountEligibleHandlesLockedState() {
        UserEntity activelyLocked = UserEntity.builder()
                .enabled(true)
                .pendingDeletion(false)
                .locked(true)
                .lockExpiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
        assertThat(AccountStatusUtil.isAccountEligible(activelyLocked)).isFalse();

        UserEntity expiredLock = UserEntity.builder()
                .enabled(true)
                .pendingDeletion(false)
                .locked(true)
                .lockExpiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        assertThat(AccountStatusUtil.isAccountEligible(expiredLock)).isTrue();
    }
}
