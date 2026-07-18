package com.example.common.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class RealtimeOutboxStateServiceTest {

    private final RealtimeOutboxRepository repository = mock(RealtimeOutboxRepository.class);
    private final RealtimeOutboxProperties properties = new RealtimeOutboxProperties();
    private final RealtimeOutboxStateService stateService =
            new RealtimeOutboxStateService(repository, properties);

    @Test
    void claimUsesClockSkewGuardForLeaseRecovery() {
        Instant now = Instant.parse("2026-07-18T00:00:00Z");
        Duration lease = Duration.ofSeconds(30);
        Duration skew = properties.getLeaseClockSkew();
        when(repository.claim(
                1L,
                "worker-a",
                now,
                now.minus(skew),
                now.plus(lease).plus(skew))).thenReturn(0);

        Optional<RealtimeOutboxClaim> claim = stateService.claim(1L, "worker-a", now, lease);

        assertThat(claim).isEmpty();
        verify(repository).claim(
                1L,
                "worker-a",
                now,
                now.minus(skew),
                now.plus(lease).plus(skew));
    }
}
