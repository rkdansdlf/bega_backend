package com.example.common.realtime.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class RealtimeAuthorizationServiceTest {

    private final RealtimeAuthorizationCache cache = mock(RealtimeAuthorizationCache.class);
    private final RealtimeAuthorizationPolicyEvaluator evaluator =
            mock(RealtimeAuthorizationPolicyEvaluator.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private RealtimeAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new RealtimeAuthorizationService(cache, evaluator, meterRegistry);
    }

    @AfterEach
    void tearDown() {
        meterRegistry.close();
    }

    @Test
    void partyCacheHitSkipsDatabasePolicy() {
        given(cache.get(RealtimeAuthorizationResource.PARTY, 10L, 123L))
                .willReturn(Optional.of(true));

        assertThat(service.canAccessParty(10L, 123L)).isTrue();

        verify(evaluator, never()).canAccessParty(10L, 123L);
        verify(cache, never()).put(
                RealtimeAuthorizationResource.PARTY, 10L, 123L, true);
        assertThat(decisionCount("party", "allow")).isEqualTo(1.0);
    }

    @Test
    void partyMissStoresOrdinaryDeny() {
        given(cache.get(RealtimeAuthorizationResource.PARTY, 10L, 123L))
                .willReturn(Optional.empty());
        given(evaluator.canAccessParty(10L, 123L)).willReturn(false);

        assertThat(service.canAccessParty(10L, 123L)).isFalse();

        verify(cache).put(RealtimeAuthorizationResource.PARTY, 10L, 123L, false);
        assertThat(decisionCount("party", "deny")).isEqualTo(1.0);
        assertThat(meterRegistry.get("realtime.authorization.db.duration")
                .tag("resource", "party").timer().count()).isEqualTo(1L);
        assertThat(meterRegistry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .map(Tag::getKey)
                .distinct()
                .toList())
                .doesNotContain("userId", "resourceId", "partyId", "roomId");
    }

    @Test
    void dmMissStoresAllow() {
        given(cache.get(RealtimeAuthorizationResource.DM, 77L, 123L))
                .willReturn(Optional.empty());
        given(evaluator.canAccessDmRoom(77L, 123L)).willReturn(true);

        assertThat(service.canAccessDmRoom(77L, 123L)).isTrue();

        verify(cache).put(RealtimeAuthorizationResource.DM, 77L, 123L, true);
    }

    @Test
    void dmMissStoresOrdinaryDeny() {
        given(cache.get(RealtimeAuthorizationResource.DM, 77L, 123L))
                .willReturn(Optional.empty());
        given(evaluator.canAccessDmRoom(77L, 123L)).willReturn(false);

        assertThat(service.canAccessDmRoom(77L, 123L)).isFalse();

        verify(cache).put(RealtimeAuthorizationResource.DM, 77L, 123L, false);
        assertThat(decisionCount("dm", "deny")).isEqualTo(1.0);
    }

    @Test
    void databaseFailureIsNotCachedAndBecomesUnavailable() {
        given(cache.get(RealtimeAuthorizationResource.PARTY, 10L, 123L))
                .willReturn(Optional.empty());
        given(evaluator.canAccessParty(10L, 123L))
                .willThrow(new IllegalStateException("database down"));

        assertThatThrownBy(() -> service.canAccessParty(10L, 123L))
                .isInstanceOf(RealtimeAuthorizationUnavailableException.class)
                .hasCauseInstanceOf(IllegalStateException.class);

        verify(cache, never()).put(
                RealtimeAuthorizationResource.PARTY, 10L, 123L, false);
        verify(cache, never()).put(
                RealtimeAuthorizationResource.PARTY, 10L, 123L, true);
        assertThat(decisionCount("party", "error")).isEqualTo(1.0);
    }

    private double decisionCount(String resource, String outcome) {
        return meterRegistry.get("realtime.authorization.decision")
                .tags("resource", resource, "outcome", outcome)
                .counter()
                .count();
    }
}
