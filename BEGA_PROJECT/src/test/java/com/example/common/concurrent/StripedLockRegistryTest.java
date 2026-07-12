package com.example.common.concurrent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import org.junit.jupiter.api.Test;

class StripedLockRegistryTest {

    @Test
    void mapsArbitraryKeysOntoFixedLockStripes() {
        StripedLockRegistry registry = new StripedLockRegistry(16);
        Set<Lock> observedLocks = new HashSet<>();

        for (int index = 0; index < 10_000; index++) {
            observedLocks.add(registry.lockFor("key-" + index));
        }

        assertThat(registry.stripeCount()).isEqualTo(16);
        assertThat(observedLocks).hasSizeLessThanOrEqualTo(16);
        assertThat(registry.lockFor("same-key")).isSameAs(registry.lockFor("same-key"));
    }
}
