package com.example.common.concurrent;

import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class StripedLockRegistry {

    private final ReentrantLock[] locks;

    public StripedLockRegistry(int stripeCount) {
        if (stripeCount <= 0) {
            throw new IllegalArgumentException("stripeCount must be positive");
        }
        this.locks = new ReentrantLock[stripeCount];
        for (int index = 0; index < stripeCount; index++) {
            locks[index] = new ReentrantLock();
        }
    }

    public Lock lockFor(Object key) {
        int hash = Objects.requireNonNull(key, "key must not be null").hashCode();
        hash ^= hash >>> 16;
        return locks[Math.floorMod(hash, locks.length)];
    }

    public int stripeCount() {
        return locks.length;
    }
}
