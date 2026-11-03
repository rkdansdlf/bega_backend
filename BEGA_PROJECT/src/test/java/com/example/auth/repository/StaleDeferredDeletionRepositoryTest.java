package com.example.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.entity.UserEntity;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
class StaleDeferredDeletionRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void staleDeferredDeletionIsCountedAndListedWhileRecentAndNonPendingAccountsAreNot() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30).truncatedTo(ChronoUnit.MICROS);
        long before = userRepository.countStaleDeferredDeletions(cutoff);
        UserEntity stale = userRepository.saveAndFlush(deletionUser("stale", true, cutoff.minusDays(1)));
        UserEntity recent = userRepository.saveAndFlush(deletionUser("recent", true, cutoff.plusDays(25)));
        UserEntity notPending = userRepository.saveAndFlush(deletionUser("notpend", false, cutoff.minusDays(10)));

        assertThat(userRepository.countStaleDeferredDeletions(cutoff)).isEqualTo(before + 1);
        assertThat(userRepository.findStaleDeferredDeletionUserIds(cutoff, PageRequest.of(0, 20)))
                .contains(stale.getId())
                .doesNotContain(recent.getId(), notPending.getId());
    }

    @Test
    void dueDeletionExactlyAtCutoffIsStaleAndTombstoneWithoutScheduleIsNot() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30).truncatedTo(ChronoUnit.MICROS);
        long before = userRepository.countStaleDeferredDeletions(cutoff);
        UserEntity atCutoff = userRepository.saveAndFlush(deletionUser("atcut", true, cutoff));
        UserEntity tombstone = userRepository.saveAndFlush(deletionUser("tomb", true, null));

        assertThat(userRepository.countStaleDeferredDeletions(cutoff)).isEqualTo(before + 1);
        assertThat(userRepository.findStaleDeferredDeletionUserIds(cutoff, PageRequest.of(0, 20)))
                .contains(atCutoff.getId())
                .doesNotContain(tombstone.getId());
    }

    @Test
    void staleDeletionsAreListedOldestFirstAndRespectThePageLimit() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30).truncatedTo(ChronoUnit.MICROS);
        UserEntity newer = userRepository.saveAndFlush(deletionUser("newer", true, cutoff.minusDays(5)));
        UserEntity older = userRepository.saveAndFlush(deletionUser("older", true, cutoff.minusDays(50)));

        assertThat(userRepository.findStaleDeferredDeletionUserIds(cutoff, PageRequest.of(0, 20)))
                .containsSubsequence(older.getId(), newer.getId());
        assertThat(userRepository.findStaleDeferredDeletionUserIds(cutoff, PageRequest.of(0, 1))).hasSize(1);
    }

    @Test
    void stalledContentRetryIsCountedAndListedWhileFutureAndUnsetMarkersAreNot() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(30).truncatedTo(ChronoUnit.MICROS);
        long before = userRepository.countStaleContentRetries(cutoff);
        UserEntity stalled = deletionUser("stall", false, null);
        stalled.setContentAnonymizationRetryAt(cutoff.minusDays(2));
        stalled = userRepository.saveAndFlush(stalled);
        UserEntity scheduled = deletionUser("sched", false, null);
        scheduled.setContentAnonymizationRetryAt(cutoff.plusHours(1));
        scheduled = userRepository.saveAndFlush(scheduled);
        UserEntity unset = userRepository.saveAndFlush(deletionUser("unset", false, null));

        assertThat(userRepository.countStaleContentRetries(cutoff)).isEqualTo(before + 1);
        assertThat(userRepository.findStaleContentRetryUserIds(cutoff, PageRequest.of(0, 20)))
                .contains(stalled.getId())
                .doesNotContain(scheduled.getId(), unset.getId());
    }

    private UserEntity deletionUser(String label, boolean pendingDeletion, LocalDateTime scheduledFor) {
        return UserEntity.builder()
                .uniqueId(UUID.randomUUID())
                .handle("sd" + UUID.randomUUID().toString().substring(0, 8))
                .name(label)
                .email(label + "-" + UUID.randomUUID() + "@example.test")
                .role("ROLE_USER")
                .enabled(!pendingDeletion)
                .locked(false)
                .tokenVersion(0)
                .pendingDeletion(pendingDeletion)
                .deletionScheduledFor(scheduledFor)
                .build();
    }
}
