package com.example.auth.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class AuthSecurityMonitoringService {

    private final Counter invalidOriginTotal;
    private final Counter tokenRejectTotal;
    private final Counter unsignedOauth2CookieTotal;
    private final Counter oauth2StateRejectTotal;

    public AuthSecurityMonitoringService(MeterRegistry meterRegistry) {
        this.invalidOriginTotal = Counter.builder("auth_security_events_total")
                .description("Invalid origin attempts on authenticated API calls")
                .tag("event", "INVALID_ORIGIN")
                .register(meterRegistry);

        this.tokenRejectTotal = Counter.builder("auth_security_events_total")
                .description("Rejected JWT / auth token attempts")
                .tag("event", "TOKEN_REJECT")
                .register(meterRegistry);

        this.unsignedOauth2CookieTotal = Counter.builder("auth_security_events_total")
                .description("Invalid or unsigned OAuth2 request cookie usage")
                .tag("event", "UNSIGNED_OAUTH2_COOKIE")
                .register(meterRegistry);

        this.oauth2StateRejectTotal = Counter.builder("auth_security_events_total")
                .description("OAuth2 state lookup or payload rejection")
                .tag("event", "OAUTH2_STATE_REJECT")
                .register(meterRegistry);
    }

    public void recordInvalidOrigin() {
        invalidOriginTotal.increment();
    }

    public void recordTokenReject() {
        tokenRejectTotal.increment();
    }

    public void recordUnsignedOauth2Cookie() {
        unsignedOauth2CookieTotal.increment();
    }

    public void recordOAuth2StateReject() {
        oauth2StateRejectTotal.increment();
    }
}
