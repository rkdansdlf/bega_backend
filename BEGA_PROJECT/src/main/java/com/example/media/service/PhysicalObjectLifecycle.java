package com.example.media.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Pure rules only: persistence must lock and version-check the physical object and its references. */
public final class PhysicalObjectLifecycle {
    public enum State { HOLD_UNVERIFIED, ACTIVE, DELETE_PENDING, DELETE_CLAIMED, DELETED }

    private final State state;
    private final long auditGeneration;
    private final long referenceRevision;
    private final long claimGeneration;
    private final boolean deletionRequested;
    private final String claimToken;
    private final Instant leaseUntil;
    private final Instant retryAt;
    private final Instant firstAbsentAt;
    private final Instant secondAbsentAt;
    private final long firstAbsenceGeneration;

    private PhysicalObjectLifecycle(State state, long auditGeneration, long referenceRevision,
            long claimGeneration, boolean deletionRequested, String claimToken, Instant leaseUntil,
            Instant retryAt, Instant firstAbsentAt, Instant secondAbsentAt, long firstAbsenceGeneration) {
        this.state = Objects.requireNonNull(state);
        this.auditGeneration = auditGeneration;
        this.referenceRevision = referenceRevision;
        this.claimGeneration = claimGeneration;
        this.deletionRequested = deletionRequested;
        this.claimToken = claimToken;
        this.leaseUntil = leaseUntil;
        this.retryAt = retryAt;
        this.firstAbsentAt = firstAbsentAt;
        this.secondAbsentAt = secondAbsentAt;
        this.firstAbsenceGeneration = firstAbsenceGeneration;
    }

    public static PhysicalObjectLifecycle unverified() {
        return new PhysicalObjectLifecycle(State.HOLD_UNVERIFIED, 0, 0, 0, false, null, null, null, null, null, 0);
    }

    public State state() { return state; }
    public long auditGeneration() { return auditGeneration; }
    public long referenceRevision() { return referenceRevision; }
    public long claimGeneration() { return claimGeneration; }
    public boolean deletionRequested() { return deletionRequested; }
    public String claimToken() { return claimToken; }
    public Instant leaseUntil() { return leaseUntil; }
    public Instant retryAt() { return retryAt; }
    public Instant firstAbsentAt() { return firstAbsentAt; }
    public Instant secondAbsentAt() { return secondAbsentAt; }
    public long firstAbsenceGeneration() { return firstAbsenceGeneration; }

    /** Internal persistence boundary; it checks shape, not whether earlier transitions were authorized. */
    static PhysicalObjectLifecycle restorePersisted(State state, long auditGeneration, long referenceRevision,
            long claimGeneration, boolean deletionRequested, String claimToken, Instant leaseUntil,
            Instant retryAt, Instant firstAbsentAt, Instant secondAbsentAt, long firstAbsenceGeneration) {
        Objects.requireNonNull(state, "state");
        if (auditGeneration < 0 || referenceRevision < 0 || claimGeneration < 0
                || (state != State.HOLD_UNVERIFIED && auditGeneration == 0)) {
            throw new IllegalArgumentException("Invalid persisted generation");
        }
        boolean claimed = state == State.DELETE_CLAIMED;
        if ((claimed && (claimGeneration == 0 || claimToken == null || claimToken.isBlank()
                || claimToken.length() > 36 || leaseUntil == null || retryAt != null))
                || (!claimed && (claimToken != null || leaseUntil != null))) {
            throw new IllegalArgumentException("Invalid persisted claim");
        }
        if ((state == State.ACTIVE && deletionRequested)
                || ((state == State.DELETE_PENDING || claimed || state == State.DELETED) && !deletionRequested)
                || (retryAt != null && (!(state == State.DELETE_PENDING || state == State.HOLD_UNVERIFIED)
                        || !deletionRequested))) {
            throw new IllegalArgumentException("Invalid persisted deletion intent");
        }
        if ((firstAbsentAt == null && firstAbsenceGeneration != 0)
                || (firstAbsentAt != null && (!deletionRequested || firstAbsenceGeneration <= 0
                        || firstAbsenceGeneration > claimGeneration
                        || state == State.ACTIVE || state == State.HOLD_UNVERIFIED
                        || (state == State.DELETE_PENDING && (retryAt == null || !retryAt.isAfter(firstAbsentAt)))
                        || (claimed && firstAbsenceGeneration >= claimGeneration)))
                || (state == State.DELETED && (firstAbsentAt == null || secondAbsentAt == null
                        || firstAbsenceGeneration >= claimGeneration || !secondAbsentAt.isAfter(firstAbsentAt)))
                || (state != State.DELETED && secondAbsentAt != null)) {
            throw new IllegalArgumentException("Invalid persisted absence evidence");
        }
        return new PhysicalObjectLifecycle(state, auditGeneration, referenceRevision, claimGeneration,
                deletionRequested, claimToken, leaseUntil, retryAt, firstAbsentAt, secondAbsentAt, firstAbsenceGeneration);
    }

    /** Caller verifies a complete audit and locks the row before comparing the captured revision. */
    public PhysicalObjectLifecycle reconciled(long generation, long auditedRevision, boolean hasReferences) {
        if (state != State.HOLD_UNVERIFIED || generation <= auditGeneration || auditedRevision != referenceRevision) {
            throw new IllegalStateException("Reference audit is stale or incomplete");
        }
        boolean pending = deletionRequested && !hasReferences;
        return copy(pending ? State.DELETE_PENDING : State.ACTIVE, generation, referenceRevision, claimGeneration,
                pending, null, null, pending ? retryAt : null, pending ? firstAbsentAt : null);
    }

    /** Only ACTIVE allows a new reference, and every mutation advances the audited revision. */
    public PhysicalObjectLifecycle referenceAdded() {
        if (state != State.ACTIVE) throw new IllegalStateException("Object cannot accept references");
        return copy(state, auditGeneration, Math.incrementExact(referenceRevision), claimGeneration,
                false, null, null, null, null);
    }

    /** Remaining reference count must be read under the same lock and transaction as removal. */
    public PhysicalObjectLifecycle referenceRemoved(long generation, long remainingReferences) {
        if ((state != State.ACTIVE && state != State.HOLD_UNVERIFIED) || remainingReferences < 0
                || generation != auditGeneration) {
            throw new IllegalStateException("Reference state changed or audit is stale");
        }
        long nextRevision = Math.incrementExact(referenceRevision);
        if (state == State.HOLD_UNVERIFIED) {
            return copy(state, auditGeneration, nextRevision, claimGeneration, deletionRequested || remainingReferences == 0,
                    null, null, retryAt, firstAbsentAt);
        }
        boolean last = remainingReferences == 0;
        return copy(last ? State.DELETE_PENDING : State.ACTIVE, auditGeneration, nextRevision,
                claimGeneration, last, null, null, null, null);
    }

    /** Explicitly request cleanup of an audited object that already has no references. */
    public PhysicalObjectLifecycle requestDeletion(long generation, long remainingReferences) {
        if (state != State.ACTIVE || generation != auditGeneration || remainingReferences != 0) {
            throw new IllegalStateException("Object is referenced, unverified, or no longer active");
        }
        return copy(State.DELETE_PENDING, auditGeneration, referenceRevision, claimGeneration,
                true, null, null, null, null);
    }

    public PhysicalObjectLifecycle holdForReconciliation() {
        if (state != State.ACTIVE && state != State.DELETE_PENDING) {
            throw new IllegalStateException("An in-flight or deleted object cannot be placed on hold");
        }
        return copy(State.HOLD_UNVERIFIED, auditGeneration, referenceRevision, claimGeneration,
                deletionRequested, null, null, retryAt, null);
    }

    public PhysicalObjectLifecycle claim(String token, Instant now, Duration lease) {
        requireToken(token);
        Objects.requireNonNull(now, "now");
        requirePositive(lease);
        boolean pendingDue = state == State.DELETE_PENDING && (retryAt == null || !retryAt.isAfter(now));
        boolean expired = state == State.DELETE_CLAIMED && !leaseUntil.isAfter(now);
        if (!deletionRequested || (!pendingDue && !expired)) {
            throw new IllegalStateException("Deletion is not available for claim");
        }
        return copy(State.DELETE_CLAIMED, auditGeneration, referenceRevision, Math.incrementExact(claimGeneration),
                true, token, now.plus(lease), null, firstAbsentAt);
    }

    /** A failed or inconclusive check resets the first-absence evidence. */
    public PhysicalObjectLifecycle retry(String token, long generation, Instant now, Duration delay) {
        requireCurrentClaim(token, generation, now);
        requirePositive(delay);
        return copy(State.DELETE_PENDING, auditGeneration, referenceRevision, claimGeneration,
                true, null, null, now.plus(delay), null);
    }

    /** Each confirmed absence is from a separate claim; the second requires the retry delay to have elapsed. */
    public PhysicalObjectLifecycle confirmAbsent(String token, long generation, Instant now, Duration recheckDelay) {
        requireCurrentClaim(token, generation, now);
        requirePositive(recheckDelay);
        if (firstAbsentAt != null) {
            if (firstAbsenceGeneration >= claimGeneration) throw new IllegalStateException("A separate claim is required");
            return new PhysicalObjectLifecycle(State.DELETED, auditGeneration, referenceRevision, claimGeneration,
                    true, null, null, null, firstAbsentAt, now, firstAbsenceGeneration);
        }
        return copy(State.DELETE_PENDING, auditGeneration, referenceRevision, claimGeneration,
                true, null, null, now.plus(recheckDelay), now);
    }

    private void requireCurrentClaim(String token, long generation, Instant now) {
        requireToken(token);
        Objects.requireNonNull(now, "now");
        if (state != State.DELETE_CLAIMED || !token.equals(claimToken) || generation != claimGeneration
                || !leaseUntil.isAfter(now)) {
            throw new IllegalStateException("Deletion claim is stale or expired");
        }
    }

    private PhysicalObjectLifecycle copy(State state, long auditGeneration, long referenceRevision,
            long claimGeneration, boolean deletionRequested, String claimToken, Instant leaseUntil,
            Instant retryAt, Instant firstAbsentAt) {
        return new PhysicalObjectLifecycle(state, auditGeneration, referenceRevision, claimGeneration,
                deletionRequested, claimToken, leaseUntil, retryAt, firstAbsentAt, null,
                firstAbsentAt == null ? 0 : (this.firstAbsentAt == null ? claimGeneration : firstAbsenceGeneration));
    }

    private static void requireToken(String token) {
        if (token == null || token.isBlank() || token.length() > 36) {
            throw new IllegalArgumentException("A claim token of at most 36 characters is required");
        }
    }

    private static void requirePositive(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("A positive duration is required");
        }
    }
}
