package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class AuthSecurityMonitoringServiceTest {

    @Test
    void authSecurityEventCounters_useStableTagKeys() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AuthSecurityMonitoringService service = new AuthSecurityMonitoringService(meterRegistry);

        service.recordInvalidOrigin();
        service.recordTokenReject();
        service.recordAuthRateLimitReject();
        service.recordRefreshReissueReject("refresh_token_missing");

        assertThat(meterRegistry.counter(
                "auth_security_events_total",
                "event", "INVALID_ORIGIN",
                "code", "NONE").count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.counter(
                "auth_security_events_total",
                "event", "TOKEN_REJECT",
                "code", "NONE").count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.counter(
                "auth_security_events_total",
                "event", "AUTH_RATE_LIMIT_REJECT",
                "code", "NONE").count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.counter(
                "auth_security_events_total",
                "event", "REFRESH_REISSUE_REJECT",
                "code", "REFRESH_TOKEN_MISSING").count())
                .isEqualTo(1.0);

        meterRegistry.getMeters().stream()
                .filter(meter -> "auth_security_events_total".equals(meter.getId().getName()))
                .forEach(meter -> assertThat(meter.getId().getTags())
                        .extracting(Tag::getKey)
                        .containsExactlyInAnyOrder("event", "code"));
    }
}
