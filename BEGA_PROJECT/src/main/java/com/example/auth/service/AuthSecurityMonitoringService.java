package com.example.auth.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class AuthSecurityMonitoringService {

    private static final String METRIC_NAME = "auth_security_events_total";
    private static final String NO_CODE = "NONE";

    private final MeterRegistry meterRegistry;
    private final Counter invalidOriginTotal;
    private final Counter tokenRejectTotal;
    private final Counter unsignedOauth2CookieTotal;
    private final Counter oauth2StateRejectTotal;
    private final Counter oauth2LinkRejectTotal;
    private final Counter oauth2LinkConflictTotal;
    private final Counter authRateLimitRejectTotal;
    private final Counter passwordResetSuppressedTotal;

    public AuthSecurityMonitoringService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.invalidOriginTotal = counter(
                "INVALID_ORIGIN",
                NO_CODE,
                "Invalid origin attempts on authenticated API calls");
        this.tokenRejectTotal = counter(
                "TOKEN_REJECT",
                NO_CODE,
                "Rejected JWT / auth token attempts");
        this.unsignedOauth2CookieTotal = counter(
                "UNSIGNED_OAUTH2_COOKIE",
                NO_CODE,
                "Invalid or unsigned OAuth2 request cookie usage");
        this.oauth2StateRejectTotal = counter(
                "OAUTH2_STATE_REJECT",
                NO_CODE,
                "OAuth2 state lookup or payload rejection");
        this.oauth2LinkRejectTotal = counter(
                "OAUTH2_LINK_REJECT",
                NO_CODE,
                "OAuth2 link ticket or link state rejection");
        this.oauth2LinkConflictTotal = counter(
                "OAUTH2_LINK_CONFLICT",
                NO_CODE,
                "OAuth2 link conflict attempts");
        this.authRateLimitRejectTotal = counter(
                "AUTH_RATE_LIMIT_REJECT",
                NO_CODE,
                "Rejected authentication-related requests due to rate limiting");
        this.passwordResetSuppressedTotal = counter(
                "PASSWORD_RESET_SUPPRESSED",
                NO_CODE,
                "Suppressed password reset requests for enumeration-resistant handling");
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

    public void recordOAuth2LinkReject() {
        oauth2LinkRejectTotal.increment();
    }

    public void recordOAuth2LinkConflict() {
        oauth2LinkConflictTotal.increment();
    }

    public void recordAuthRateLimitReject() {
        authRateLimitRejectTotal.increment();
    }

    public void recordPasswordResetSuppressed() {
        passwordResetSuppressedTotal.increment();
    }

    public void recordRefreshReissueReject(String code) {
        counter(
                "REFRESH_REISSUE_REJECT",
                normalizeCode(code),
                "Rejected refresh token reissue attempts")
                .increment();
    }

    private Counter counter(String event, String code, String description) {
        return Counter.builder(METRIC_NAME)
                .description(description)
                .tag("event", event)
                .tag("code", normalizeCode(code))
                .register(meterRegistry);
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return "UNKNOWN";
        }

        return code.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_\\-]", "_");
    }
}
