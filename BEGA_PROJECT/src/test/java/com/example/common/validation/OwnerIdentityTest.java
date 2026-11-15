package com.example.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OwnerIdentityTest {

    @Test
    void sameNonNullOwnerMatches() {
        assertThat(OwnerIdentity.isSameOwner(42L, 42L)).isTrue();
    }

    @Test
    void differentOwnersDoNotMatch() {
        assertThat(OwnerIdentity.isSameOwner(42L, 7L)).isFalse();
    }

    @Test
    void nullOwnerNeverMatchesEvenWhenUserIsNull() {
        assertThat(OwnerIdentity.isSameOwner(null, null)).isFalse();
    }

    @Test
    void nullOwnerDoesNotMatchAnyUser() {
        assertThat(OwnerIdentity.isSameOwner(null, 42L)).isFalse();
    }

    @Test
    void nullUserDoesNotMatchAnyOwner() {
        assertThat(OwnerIdentity.isSameOwner(42L, null)).isFalse();
    }
}
