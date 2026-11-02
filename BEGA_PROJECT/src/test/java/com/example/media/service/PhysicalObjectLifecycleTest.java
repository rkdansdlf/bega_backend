package com.example.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PhysicalObjectLifecycleTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(1);

    @Test
    void auditWithoutDeletionIntentDoesNotDeleteAnUnreferencedObject() {
        var held = PhysicalObjectLifecycle.unverified();
        assertThatThrownBy(() -> held.claim("token", NOW, LEASE)).isInstanceOf(IllegalStateException.class);
        var active = held.reconciled(1, 0, false);
        assertThat(active.state()).isEqualTo(PhysicalObjectLifecycle.State.ACTIVE);
        assertThat(active.deletionRequested()).isFalse();
        assertThatThrownBy(() -> active.claim("token", NOW, LEASE)).isInstanceOf(IllegalStateException.class);
        assertThat(active.requestDeletion(1, 0).state()).isEqualTo(PhysicalObjectLifecycle.State.DELETE_PENDING);
    }

    @Test
    void referenceMutationDuringHoldInvalidatesAnOlderAudit() {
        var held = PhysicalObjectLifecycle.unverified().referenceRemoved(0, 0);
        assertThat(held.deletionRequested()).isTrue();
        assertThatThrownBy(() -> held.reconciled(1, 0, false)).isInstanceOf(IllegalStateException.class);
        assertThat(held.reconciled(1, 1, false).state()).isEqualTo(PhysicalObjectLifecycle.State.DELETE_PENDING);
    }

    @Test
    void sharedReferenceRemovalDoesNotRequestPhysicalDeletion() {
        var active = PhysicalObjectLifecycle.unverified().reconciled(7, 0, true).referenceAdded();
        assertThatThrownBy(() -> active.referenceRemoved(6, 0)).isInstanceOf(IllegalStateException.class);
        var remaining = active.referenceRemoved(7, 1);
        assertThat(remaining.state()).isEqualTo(PhysicalObjectLifecycle.State.ACTIVE);
        assertThat(remaining.deletionRequested()).isFalse();
        assertThat(remaining.referenceRemoved(7, 0).state()).isEqualTo(PhysicalObjectLifecycle.State.DELETE_PENDING);
    }

    @Test
    void reusedTokenCannotFenceOutItsOlderClaimGeneration() {
        var pending = PhysicalObjectLifecycle.unverified().reconciled(1, 0, false).requestDeletion(1, 0);
        var first = pending.claim("same", NOW, LEASE);
        var retry = first.retry("same", first.claimGeneration(), NOW.plusSeconds(1), Duration.ofSeconds(1));
        var second = retry.claim("same", NOW.plusSeconds(2), LEASE);
        assertThat(second.claimGeneration()).isGreaterThan(first.claimGeneration());
        assertThatThrownBy(() -> second.confirmAbsent("same", first.claimGeneration(), NOW.plusSeconds(3), LEASE))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> first.confirmAbsent("same", first.claimGeneration(), NOW.plusSeconds(60), LEASE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void twoSeparateDelayedAbsenceChecksAreRequiredAndDeletedKeysStayFenced() {
        var first = PhysicalObjectLifecycle.unverified().reconciled(1, 0, false).requestDeletion(1, 0)
                .claim("one", NOW, LEASE);
        var pending = first.confirmAbsent("one", first.claimGeneration(), NOW.plusSeconds(1), Duration.ofSeconds(10));
        assertThat(pending.firstAbsentAt()).isEqualTo(NOW.plusSeconds(1));
        assertThatThrownBy(() -> pending.claim("two", NOW.plusSeconds(10), LEASE)).isInstanceOf(IllegalStateException.class);
        var second = pending.claim("two", NOW.plusSeconds(11), LEASE);
        var deleted = second.confirmAbsent("two", second.claimGeneration(), NOW.plusSeconds(12), LEASE);
        assertThat(deleted.state()).isEqualTo(PhysicalObjectLifecycle.State.DELETED);
        assertThat(deleted.secondAbsentAt()).isEqualTo(NOW.plusSeconds(12));
        assertThatThrownBy(deleted::referenceAdded).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> deleted.reconciled(2, 0, false)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void persistedPendingStateRestoresGenerationAndFirstAbsence() {
        var first = PhysicalObjectLifecycle.unverified().reconciled(1, 0, false).requestDeletion(1, 0)
                .claim("one", NOW, LEASE);
        var pending = first.confirmAbsent("one", first.claimGeneration(), NOW.plusSeconds(1), Duration.ofSeconds(10));
        var restored = PhysicalObjectLifecycle.restorePersisted(pending.state(), pending.auditGeneration(),
                pending.referenceRevision(), pending.claimGeneration(), pending.deletionRequested(),
                pending.claimToken(), pending.leaseUntil(), pending.retryAt(), pending.firstAbsentAt(), pending.secondAbsentAt(),
                pending.firstAbsenceGeneration());
        assertThat(restored.claimGeneration()).isEqualTo(1);
        assertThat(restored.firstAbsentAt()).isEqualTo(NOW.plusSeconds(1));
        assertThatThrownBy(() -> restored.claim("two", NOW.plusSeconds(10), LEASE)).isInstanceOf(IllegalStateException.class);
        var second = restored.claim("two", NOW.plusSeconds(11), LEASE);
        assertThat(second.confirmAbsent("two", second.claimGeneration(), NOW.plusSeconds(12), LEASE).state())
                .isEqualTo(PhysicalObjectLifecycle.State.DELETED);
    }

    @Test
    void restorationRejectsIncompleteOrOutOfOrderAbsenceEvidence() {
        assertThatThrownBy(() -> PhysicalObjectLifecycle.restorePersisted(
                PhysicalObjectLifecycle.State.DELETED, 1, 0, 1, true, null, null, null, NOW, NOW.plusSeconds(1), 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PhysicalObjectLifecycle.restorePersisted(
                PhysicalObjectLifecycle.State.DELETED, 1, 0, 2, true, null, null, null, NOW, NOW, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PhysicalObjectLifecycle.restorePersisted(
                PhysicalObjectLifecycle.State.DELETE_PENDING, 1, 0, 1, true, null, null, NOW, NOW, null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PhysicalObjectLifecycle.restorePersisted(
                PhysicalObjectLifecycle.State.DELETED, 1, 0, 2, true, null, null, null, NOW, NOW.plusSeconds(1), 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failureResetsAbsenceEvidenceAndHoldPreservesBackoff() {
        var first = PhysicalObjectLifecycle.unverified().reconciled(1, 0, false).requestDeletion(1, 0)
                .claim("one", NOW, LEASE);
        var pending = first.confirmAbsent("one", first.claimGeneration(), NOW.plusSeconds(1), Duration.ofSeconds(10));
        var held = pending.holdForReconciliation();
        assertThat(held.retryAt()).isEqualTo(NOW.plusSeconds(11));
        assertThat(held.firstAbsentAt()).isNull();
        assertThatThrownBy(() -> held.reconciled(1, 0, false)).isInstanceOf(IllegalStateException.class);
        var restored = held.reconciled(2, 0, false);
        assertThatThrownBy(() -> restored.claim("two", NOW.plusSeconds(10), LEASE)).isInstanceOf(IllegalStateException.class);
        var second = restored.claim("two", NOW.plusSeconds(11), LEASE);
        assertThat(second.confirmAbsent("two", second.claimGeneration(), NOW.plusSeconds(12), LEASE).state())
                .isEqualTo(PhysicalObjectLifecycle.State.DELETE_PENDING);
        var failed = second.retry("two", second.claimGeneration(), NOW.plusSeconds(12), Duration.ofSeconds(5));
        assertThat(failed.firstAbsentAt()).isNull();
        var third = failed.claim("three", NOW.plusSeconds(17), LEASE);
        assertThat(third.confirmAbsent("three", third.claimGeneration(), NOW.plusSeconds(18), LEASE).state())
                .isEqualTo(PhysicalObjectLifecycle.State.DELETE_PENDING);
    }
}
