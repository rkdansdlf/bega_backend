package com.example.auth.repository;

import com.example.auth.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findUsableRoleByIdAndTokenVersion_returnsRoleForMatchingTokenVersion() {
        UserEntity user = userRepository.saveAndFlush(usableUser("match@example.com", "match", 3));

        Optional<String> role = userRepository.findUsableRoleByIdAndTokenVersion(user.getId(), 3);

        assertThat(role).contains("ROLE_USER");
    }

    @Test
    void findUsableRoleByIdAndTokenVersion_allowsLegacyNullTokenVersionOnlyForCurrentZero() {
        UserEntity user = userRepository.saveAndFlush(usableUser("legacy@example.com", "legacy", 0));

        Optional<String> role = userRepository.findUsableRoleByIdAndTokenVersion(user.getId(), null);

        assertThat(role).contains("ROLE_USER");
    }

    @Test
    void findUsableRoleByIdAndTokenVersion_rejectsStaleTokenVersion() {
        UserEntity user = userRepository.saveAndFlush(usableUser("stale@example.com", "stale", 2));

        Optional<String> role = userRepository.findUsableRoleByIdAndTokenVersion(user.getId(), 1);

        assertThat(role).isEmpty();
    }

    @Test
    void findUsableRoleByIdAndTokenVersion_rejectsDisabledUser() {
        UserEntity user = usableUser("disabled@example.com", "disabled", 0);
        user.setEnabled(false);
        user = userRepository.saveAndFlush(user);

        Optional<String> role = userRepository.findUsableRoleByIdAndTokenVersion(user.getId(), 0);

        assertThat(role).isEmpty();
    }

    @Test
    void findUsableRoleByIdAndTokenVersion_rejectsActiveLock() {
        UserEntity user = usableUser("locked@example.com", "locked", 0);
        user.setLocked(true);
        user.setLockExpiresAt(LocalDateTime.now().plusMinutes(5));
        user = userRepository.saveAndFlush(user);

        Optional<String> role = userRepository.findUsableRoleByIdAndTokenVersion(user.getId(), 0);

        assertThat(role).isEmpty();
    }

    @Test
    void findUsableRoleByIdAndTokenVersion_allowsExpiredLock() {
        UserEntity user = usableUser("unlocked@example.com", "unlocked", 0);
        user.setLocked(true);
        user.setLockExpiresAt(LocalDateTime.now().minusMinutes(5));
        user = userRepository.saveAndFlush(user);

        Optional<String> role = userRepository.findUsableRoleByIdAndTokenVersion(user.getId(), 0);

        assertThat(role).contains("ROLE_USER");
    }

    private UserEntity usableUser(String email, String handle, Integer tokenVersion) {
        return UserEntity.builder()
                .uniqueId(UUID.randomUUID())
                .handle(handle)
                .name(handle)
                .email(email)
                .role("ROLE_USER")
                .enabled(true)
                .locked(false)
                .tokenVersion(tokenVersion)
                .build();
    }
}
