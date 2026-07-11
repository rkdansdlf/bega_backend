package com.example.ai.service;

import com.example.ai.exception.AiProxyException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiProxyStreamConcurrencyLimiterTest {

    @Test
    void acquireRecordsActiveGaugeAndAcceptedCounter() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AiProxyStreamConcurrencyLimiter limiter = new AiProxyStreamConcurrencyLimiter(2, meterRegistry);

        AiProxyStreamConcurrencyLimiter.Permit permit = limiter.acquire("chat_stream");

        assertThat(limiter.getActiveStreams()).isEqualTo(1);
        assertThat(meterRegistry.get("ai_proxy_stream_active").gauge().value()).isEqualTo(1.0);
        assertThat(meterRegistry.get("ai_proxy_stream_limit").gauge().value()).isEqualTo(2.0);
        assertThat(meterRegistry.get("ai_proxy_stream_events_total")
                        .tag("endpoint", "chat_stream")
                        .tag("result", "accepted")
                        .counter()
                        .count())
                .isEqualTo(1.0);

        permit.close();

        assertThat(limiter.getActiveStreams()).isZero();
        assertThat(meterRegistry.get("ai_proxy_stream_active").gauge().value()).isZero();
    }

    @Test
    void acquireRejectsAndRecordsRejectedCounterWhenSaturated() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AiProxyStreamConcurrencyLimiter limiter = new AiProxyStreamConcurrencyLimiter(1, meterRegistry);
        AiProxyStreamConcurrencyLimiter.Permit permit = limiter.acquire("coach_analyze");

        assertThatThrownBy(() -> limiter.acquire("coach_analyze"))
                .isInstanceOf(AiProxyException.class)
                .satisfies(throwable -> {
                    AiProxyException exception = (AiProxyException) throwable;
                    assertThat(exception.getStatus().value()).isEqualTo(503);
                    assertThat(exception.getCode()).isEqualTo(AiProxyStreamConcurrencyLimiter.STREAMS_BUSY_CODE);
                });

        assertThat(limiter.getActiveStreams()).isEqualTo(1);
        assertThat(meterRegistry.get("ai_proxy_stream_events_total")
                        .tag("endpoint", "coach_analyze")
                        .tag("result", "rejected")
                        .counter()
                        .count())
                .isEqualTo(1.0);

        permit.close();
    }

    @Test
    void unknownEndpointIsNormalized() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AiProxyStreamConcurrencyLimiter limiter = new AiProxyStreamConcurrencyLimiter(1, meterRegistry);

        limiter.acquire("/api/ai/unexpected").close();

        assertThat(meterRegistry.get("ai_proxy_stream_events_total")
                        .tag("endpoint", "unknown")
                        .tag("result", "accepted")
                        .counter()
                        .count())
                .isEqualTo(1.0);
    }
}
