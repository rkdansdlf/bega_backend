package com.example.media.service;

import com.example.cheerboard.storage.strategy.StorageObjectIdentity;
import com.example.media.entity.PhysicalMediaObject;
import com.example.media.repository.PhysicalMediaObjectLockRepository;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Registration only: HOLD_UNVERIFIED is never evidence that a deletion is safe.
 * Callers must collect all identities and call once per transaction, before taking object-row locks.
 * Do not register inside a new transaction while an outer transaction holds registry locks.
 * An empty collection consumes the single call; failures must not be retried in the same transaction.
 * A serialization or uniqueness failure requires retrying the whole transaction.
 */
@Service
@RequiredArgsConstructor
public class PhysicalMediaRegistry {
    private static final Object TRANSACTION_GUARD = new Object();
    private final PhysicalMediaCreationLock creationLock;
    private final PhysicalMediaObjectLockRepository objectLocks;
    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.MANDATORY)
    public List<PhysicalMediaObject> lockOrRegisterAll(Collection<StorageObjectIdentity> identities) {
        Objects.requireNonNull(identities, "identities");
        var ordered = new TreeMap<String, StorageObjectIdentity>();
        for (var identity : identities) {
            Objects.requireNonNull(identity, "identity");
            if (identity.bucket().length() > 255 || identity.key().length() > 2048) {
                throw new IllegalArgumentException("Physical object identity exceeds registry limits");
            }
            var prior = ordered.putIfAbsent(identity.digest(), identity);
            if (prior != null && !prior.equals(identity)) {
                throw new IllegalStateException("Physical object digest collision");
            }
        }
        claimTransaction();
        creationLock.lockAll(ordered.keySet());
        var result = new java.util.ArrayList<PhysicalMediaObject>(ordered.size());
        for (var identity : ordered.values()) {
            var object = objectLocks.lockIfRegistered(identity);
            if (object.isEmpty()) {
                jdbc.update("""
                        INSERT INTO physical_media_objects
                            (object_hash, physical_bucket, object_key, state, audit_generation, reference_revision,
                             claim_generation, deletion_requested, first_absence_generation, attempt_count, created_at, updated_at)
                        VALUES (?, ?, ?, 'HOLD_UNVERIFIED', 0, 0, 0, ?, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """, identity.digest(), identity.bucket(), identity.key(), false);
                object = java.util.Optional.of(objectLocks.lockExisting(identity));
            }
            result.add(object.orElseThrow());
        }
        return List.copyOf(result);
    }

    private static void claimTransaction() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Physical media registry requires transaction synchronization");
        }
        if (TransactionSynchronizationManager.hasResource(TRANSACTION_GUARD)) {
            throw new IllegalStateException("Physical media registry may be called only once per transaction");
        }
        Object marker = new Object();
        TransactionSynchronizationManager.bindResource(TRANSACTION_GUARD, marker);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void suspend() {
                TransactionSynchronizationManager.unbindResource(TRANSACTION_GUARD);
            }

            @Override
            public void resume() {
                TransactionSynchronizationManager.bindResource(TRANSACTION_GUARD, marker);
            }

            @Override
            public void afterCompletion(int status) {
                if (TransactionSynchronizationManager.getResource(TRANSACTION_GUARD) == marker) {
                    TransactionSynchronizationManager.unbindResource(TRANSACTION_GUARD);
                }
            }
        });
    }
}
