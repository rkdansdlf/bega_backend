package com.example.media.repository;

import com.example.cheerboard.storage.strategy.StorageObjectIdentity;
import com.example.media.entity.PhysicalMediaObject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Locks existing rows only. The caller must keep its transaction open while using the lock. */
@Repository
@RequiredArgsConstructor
public class PhysicalMediaObjectLockRepository {
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public PhysicalMediaObject lockExisting(StorageObjectIdentity identity) {
        return lockIfRegistered(identity).orElseThrow(() -> new IllegalStateException("Physical media object is not registered"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<PhysicalMediaObject> lockIfRegistered(StorageObjectIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        PhysicalMediaObject object = entityManager.find(
                PhysicalMediaObject.class, identity.digest(), LockModeType.PESSIMISTIC_WRITE);
        if (object == null) {
            return Optional.empty();
        }
        // A previous read in this persistence context may predate the lock.
        entityManager.refresh(object, LockModeType.PESSIMISTIC_WRITE);
        if (!identity.bucket().equals(object.getPhysicalBucket()) || !identity.key().equals(object.getObjectKey())) {
            throw new IllegalStateException("Physical media object identity mismatch");
        }
        return Optional.of(object);
    }
}
