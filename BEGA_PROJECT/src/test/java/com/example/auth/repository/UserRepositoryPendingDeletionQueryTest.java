package com.example.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.entity.UserEntity;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class UserRepositoryPendingDeletionQueryTest {
    @Autowired UserRepository users;

    @Test
    void unknownUserMatchesNeitherQuery() {
        assertThat(users.existsByIdAndPendingDeletionTrue(-1L)).isFalse();
        assertThat(users.existsByIdAndPendingDeletionTrueAndDeletionScheduledForIsNotNull(-1L)).isFalse();
    }

    @Test
    void activeUserMatchesNeitherQuery() {
        Long id = user(false, null).getId();

        assertThat(users.existsByIdAndPendingDeletionTrue(id)).isFalse();
        assertThat(users.existsByIdAndPendingDeletionTrueAndDeletionScheduledForIsNotNull(id)).isFalse();
    }

    @Test
    void pendingUserWithScheduleMatchesBothQueries() {
        Long id = user(true, LocalDateTime.now().plusDays(7)).getId();

        assertThat(users.existsByIdAndPendingDeletionTrue(id)).isTrue();
        assertThat(users.existsByIdAndPendingDeletionTrueAndDeletionScheduledForIsNotNull(id)).isTrue();
    }

    @Test
    void pendingUserWithoutScheduleMatchesOnlyThePendingQuery() {
        Long id = user(true, null).getId();

        assertThat(users.existsByIdAndPendingDeletionTrue(id)).isTrue();
        assertThat(users.existsByIdAndPendingDeletionTrueAndDeletionScheduledForIsNotNull(id)).isFalse();
    }

    @Test
    void queriesDoNotLeakAcrossUsers() {
        Long pendingId = user(true, LocalDateTime.now().plusDays(7)).getId();
        Long activeId = user(false, null).getId();

        assertThat(users.existsByIdAndPendingDeletionTrue(activeId)).isFalse();
        assertThat(users.existsByIdAndPendingDeletionTrueAndDeletionScheduledForIsNotNull(activeId)).isFalse();
        assertThat(users.existsByIdAndPendingDeletionTrue(pendingId)).isTrue();
    }

    private UserEntity user(boolean pending, LocalDateTime scheduledFor) {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        return users.saveAndFlush(UserEntity.builder().uniqueId(UUID.randomUUID())
                .handle("repo-" + suffix).name("Synthetic")
                .email("repo-" + suffix + "@example.invalid")
                .role("ROLE_USER").enabled(!pending).pendingDeletion(pending)
                .deletionScheduledFor(scheduledFor).locked(false).build());
    }
}
